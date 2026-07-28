from __future__ import annotations

import hashlib
import json
import time
from collections.abc import Awaitable, Mapping
from dataclasses import dataclass
from typing import Any, cast
from uuid import uuid4

from redis.asyncio import Redis

from app.modules.interview.api.diagnostics import InterviewDiagnostic

SESSION_RETENTION_SECONDS = 7 * 24 * 60 * 60
CHECKPOINT_RETENTION_SECONDS = 7 * 24 * 60 * 60
LEASE_SECONDS = 30
LEASE_HEARTBEAT_SECONDS = 10
TOKEN_TTL_SECONDS = 900
RESUME_RETENTION_SECONDS = 30 * 24 * 60 * 60
EVENT_RETENTION_SECONDS = 30 * 24 * 60 * 60

_RENEW_LEASE = """
if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
redis.call('EXPIRE', KEYS[1], ARGV[2])
return 1
"""

_RELEASE_LEASE = """
if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
return 0
"""


class InterviewRedisKeys:
    def __init__(self, prefix: str) -> None:
        self._prefix = prefix.rstrip(":")

    def session(self, session_id: str) -> str:
        return f"{self._prefix}:interview:session:{session_id}"

    def checkpoint(self, session_id: str) -> str:
        return f"{self._prefix}:interview:checkpoint:{session_id}"

    def lease(self, session_id: str) -> str:
        return f"{self._prefix}:interview:lease:{session_id}"

    def token(self, token: str) -> str:
        digest = hashlib.sha256(token.encode("utf-8")).hexdigest()
        return self.token_hash(digest)

    def token_hash(self, digest: str) -> str:
        return f"{self._prefix}:interview:token:{digest}"

    def global_concurrency(self) -> str:
        return f"{self._prefix}:interview:concurrency:global"

    def tenant_concurrency(self, tenant_id: str) -> str:
        return f"{self._prefix}:interview:concurrency:tenant:{tenant_id}"

    def resume(self, analysis_id: str) -> str:
        return f"{self._prefix}:interview:resume:{analysis_id}"

    def event(self, event_id: str) -> str:
        return f"{self._prefix}:interview:event:{event_id}"

    def recovery_index(self) -> str:
        return f"{self._prefix}:interview:recovery"

    def pending_outcome(self, analysis_id: str) -> str:
        return f"{self._prefix}:interview:resume-outcome:{analysis_id}"


def canonical_payload(payload: bytes | str | Mapping[str, Any]) -> bytes:
    if isinstance(payload, Mapping):
        value: Any = payload
    else:
        raw = payload.encode("utf-8") if isinstance(payload, str) else payload
        value = json.loads(raw)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode(
        "utf-8"
    )


def payload_sha256(payload: bytes | str | Mapping[str, Any]) -> str:
    return hashlib.sha256(canonical_payload(payload)).hexdigest()


@dataclass(frozen=True, slots=True)
class PreparedSessionWrite:
    created: bool
    payload_hash: str


@dataclass(frozen=True, slots=True)
class RuntimePreparation:
    created: bool
    payload_hash: str
    expires_at_epoch_seconds: int


@dataclass(frozen=True, slots=True)
class RuntimeTokenClaim:
    session_id: str
    call_attempt_id: str
    tenant_id: str
    same_call_replay: bool


@dataclass(frozen=True, slots=True)
class RuntimeCheckpoint:
    revision: int
    phase: str
    runtime_state: dict[str, Any]
    pending_event: dict[str, Any] | None
    call_sid: str
    recovery_deadline_epoch_seconds: int | None


@dataclass(frozen=True, slots=True)
class CheckpointRecovery:
    event_id: str | None
    revision: int
    phase: str


_CLAIM_TOKEN = """
local token = redis.call('GET', KEYS[1])
if not token then return {-1} end
local metadata = cjson.decode(token)
if tonumber(metadata.expires_at) <= tonumber(ARGV[1]) then
  redis.call('DEL', KEYS[1])
  return {-1}
end
local session_raw = redis.call('GET', KEYS[2])
if not session_raw then return {-1} end
local session = cjson.decode(session_raw)
if session.status == 'terminal' then return {-1} end
if session.claimed_call_sid and session.claimed_call_sid ~= '' then
  if session.claimed_call_sid ~= ARGV[2] then return {-2} end
  redis.call('ZADD', KEYS[3], tonumber(ARGV[1]) + tonumber(ARGV[3]), metadata.call_attempt_id)
  redis.call('ZADD', KEYS[4], tonumber(ARGV[1]) + tonumber(ARGV[3]), metadata.call_attempt_id)
  redis.call('ZADD', KEYS[5], tonumber(ARGV[1]) + tonumber(ARGV[3]), metadata.session_id)
  return {2}
end
redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[1])
redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', ARGV[1])
if redis.call('ZCARD', KEYS[3]) >= tonumber(ARGV[4]) then return {-3} end
if redis.call('ZCARD', KEYS[4]) >= tonumber(ARGV[5]) then return {-4} end
session.claimed_call_sid = ARGV[2]
session.status = 'claimed'
redis.call('SET', KEYS[2], cjson.encode(session), 'EX', ARGV[6])
redis.call('ZADD', KEYS[3], tonumber(ARGV[1]) + tonumber(ARGV[3]), metadata.call_attempt_id)
redis.call('ZADD', KEYS[4], tonumber(ARGV[1]) + tonumber(ARGV[3]), metadata.call_attempt_id)
redis.call('ZADD', KEYS[5], tonumber(ARGV[1]) + tonumber(ARGV[3]), metadata.session_id)
return {1}
"""

_RENEW_SESSION = """
local session_raw = redis.call('GET', KEYS[1])
if not session_raw then return 0 end
local session = cjson.decode(session_raw)
if session.claimed_call_sid ~= ARGV[1] or session.status == 'terminal' then return 0 end
redis.call('EXPIRE', KEYS[1], ARGV[4])
redis.call('ZADD', KEYS[2], tonumber(ARGV[2]) + tonumber(ARGV[3]), session.call_attempt_id)
redis.call('ZADD', KEYS[3], tonumber(ARGV[2]) + tonumber(ARGV[3]), session.call_attempt_id)
redis.call('ZADD', KEYS[4], tonumber(ARGV[2]) + tonumber(ARGV[3]), session.session_id)
return 1
"""

_TERMINALIZE_SESSION = """
local session_raw = redis.call('GET', KEYS[1])
if not session_raw then return 0 end
local session = cjson.decode(session_raw)
if session.call_attempt_id ~= ARGV[1] then return -1 end
if session.claimed_call_sid ~= ARGV[2] then return -1 end
session.status = 'terminal'
redis.call('SET', KEYS[1], cjson.encode(session), 'EX', ARGV[3])
if session.token_sha256 and session.token_sha256 ~= '' then redis.call('DEL', KEYS[2]) end
redis.call('ZREM', KEYS[3], ARGV[1])
redis.call('ZREM', KEYS[4], ARGV[1])
redis.call('ZREM', KEYS[5], session.session_id)
redis.call('DEL', KEYS[6])
return 1
"""

_CAS_CHECKPOINT = """
local current = redis.call('GET', KEYS[1])
if not current then
  if tonumber(ARGV[1]) ~= 0 then return {-1} end
else
  local decoded = cjson.decode(current)
  if tonumber(decoded.revision) ~= tonumber(ARGV[1]) then return {-1} end
end
local document = cjson.decode(ARGV[2])
document.revision = tonumber(ARGV[1]) + 1
redis.call('SET', KEYS[1], cjson.encode(document), 'EX', ARGV[3])
return {document.revision}
"""

_COMMIT_PENDING_EVENT = """
local current = redis.call('GET', KEYS[1])
if not current then return {-2} end
local document = cjson.decode(current)
if tonumber(document.revision) ~= tonumber(ARGV[1]) then return {-1} end
if not document.pending_event or document.pending_event.event_id ~= ARGV[2] then return {-3} end
if redis.call('EXISTS', KEYS[2]) ~= 1 then return {-4} end
document.runtime_state = document.pending_event.runtime_state
document.phase = document.pending_event.commit_phase
document.pending_event = cjson.null
document.revision = tonumber(ARGV[1]) + 1
redis.call('SET', KEYS[1], cjson.encode(document), 'EX', ARGV[3])
return {document.revision}
"""


class InterviewRedisState:
    """Durable runtime state; deliberately independent of CACHE_ENABLED."""

    def __init__(self, redis_client: Redis, *, prefix: str) -> None:
        self._redis = redis_client
        self.keys = InterviewRedisKeys(prefix)

    async def inspect(self) -> InterviewDiagnostic:
        now = time.time()
        active, recovery_due = await self._redis.zcount(
            self.keys.global_concurrency(), now, "+inf"
        ), await self._redis.zcount(self.keys.recovery_index(), "-inf", now)
        return InterviewDiagnostic(
            active_session_count=int(active),
            recovery_due_count=int(recovery_due),
        )

    async def store_prepared_session(
        self, session_id: str, payload: bytes | str | Mapping[str, Any]
    ) -> PreparedSessionWrite:
        canonical = canonical_payload(payload)
        digest = hashlib.sha256(canonical).hexdigest()
        value = json.dumps(
            {"payload_hash": digest, "payload": canonical.decode("utf-8")},
            separators=(",", ":"),
        ).encode("utf-8")
        key = self.keys.session(session_id)
        existing = await self._redis.get(key)
        if existing is not None:
            current = json.loads(_text(existing))
            if current.get("payload_hash") != digest:
                raise ValueError("Prepared interview payload hash mismatch")
            await self._redis.expire(key, SESSION_RETENTION_SECONDS)
            return PreparedSessionWrite(created=False, payload_hash=digest)
        await self._redis.set(key, value, ex=SESSION_RETENTION_SECONDS)
        return PreparedSessionWrite(created=True, payload_hash=digest)

    async def store_checkpoint(self, session_id: str, payload: bytes) -> None:
        await self._redis.set(
            self.keys.checkpoint(session_id), payload, ex=CHECKPOINT_RETENTION_SECONDS
        )

    async def load_checkpoint(self, session_id: str) -> RuntimeCheckpoint | None:
        raw = await self._redis.get(self.keys.checkpoint(session_id))
        if raw is None:
            return None
        value = json.loads(_text(raw))
        return RuntimeCheckpoint(
            revision=int(value["revision"]),
            phase=str(value["phase"]),
            runtime_state=cast(dict[str, Any], value.get("runtime_state") or {}),
            pending_event=cast(dict[str, Any] | None, value.get("pending_event")),
            call_sid=str(value.get("call_sid") or ""),
            recovery_deadline_epoch_seconds=(
                int(value["recovery_deadline_epoch_seconds"])
                if value.get("recovery_deadline_epoch_seconds") is not None
                else None
            ),
        )

    async def cas_checkpoint(
        self,
        session_id: str,
        *,
        expected_revision: int,
        phase: str,
        runtime_state: Mapping[str, Any],
        pending_event: Mapping[str, Any] | None = None,
        call_sid: str = "",
        recovery_deadline_epoch_seconds: int | None = None,
    ) -> int:
        document = {
            "version": 1,
            "revision": expected_revision,
            "phase": phase,
            "runtime_state": runtime_state,
            "pending_event": pending_event,
            "call_sid": call_sid,
            "recovery_deadline_epoch_seconds": recovery_deadline_epoch_seconds,
        }
        result = await cast(
            Awaitable[Any],
            self._redis.eval(
                _CAS_CHECKPOINT,
                1,
                self.keys.checkpoint(session_id),
                expected_revision,
                json.dumps(
                    document, ensure_ascii=False, sort_keys=True, separators=(",", ":")
                ),
                CHECKPOINT_RETENTION_SECONDS,
            ),
        )
        revision = int(result[0])
        if revision < 0:
            raise ValueError("Interview checkpoint revision conflict")
        return revision

    async def stage_checkpoint_event(
        self,
        session_id: str,
        *,
        expected_revision: int,
        phase: str,
        commit_phase: str,
        current_runtime_state: Mapping[str, Any],
        next_runtime_state: Mapping[str, Any],
        event_id: str,
        routing_key: str,
        payload: bytes,
        call_sid: str,
    ) -> int:
        pending = {
            "event_id": event_id,
            "routing_key": routing_key,
            "payload": payload.decode("utf-8"),
            "runtime_state": next_runtime_state,
            "commit_phase": commit_phase,
        }
        return await self.cas_checkpoint(
            session_id,
            expected_revision=expected_revision,
            phase=phase,
            runtime_state=current_runtime_state,
            pending_event=pending,
            call_sid=call_sid,
        )

    async def commit_checkpoint_event(
        self, session_id: str, *, expected_revision: int, event_id: str
    ) -> int:
        result = await cast(
            Awaitable[Any],
            self._redis.eval(
                _COMMIT_PENDING_EVENT,
                2,
                self.keys.checkpoint(session_id),
                self.keys.event(event_id),
                expected_revision,
                event_id,
                CHECKPOINT_RETENTION_SECONDS,
            ),
        )
        revision = int(result[0])
        if revision == -4:
            raise ValueError("Interview event has no confirmed-publication marker")
        if revision < 0:
            raise ValueError("Interview pending-event commit conflict")
        return revision

    async def mark_recoverable(
        self, session_id: str, *, recover_at_epoch_seconds: int
    ) -> None:
        await self._redis.zadd(
            self.keys.recovery_index(), {session_id: recover_at_epoch_seconds}
        )

    async def clear_recoverable(self, session_id: str) -> None:
        await self._redis.zrem(self.keys.recovery_index(), session_id)

    async def due_recoveries(self, *, now_epoch_seconds: int, limit: int = 100) -> list[str]:
        values = await self._redis.zrangebyscore(
            self.keys.recovery_index(), "-inf", now_epoch_seconds, start=0, num=limit
        )
        return [_text(item) for item in values]

    async def delete_checkpoint(self, session_id: str) -> None:
        await self._redis.delete(self.keys.checkpoint(session_id))

    async def store_runtime_token(self, token: str, session_id: str) -> None:
        await self._redis.set(
            self.keys.token(token), session_id.encode("utf-8"), ex=TOKEN_TTL_SECONDS
        )

    async def prepare_runtime_session(
        self,
        *,
        session_id: str,
        call_attempt_id: str,
        tenant_id: str,
        payload: Mapping[str, Any],
        payload_hash: str,
        token_sha256: str,
        expires_at_epoch_seconds: int,
    ) -> RuntimePreparation:
        key = self.keys.session(session_id)
        existing = await self._redis.get(key)
        if existing is not None:
            current = json.loads(_text(existing))
            if current.get("status") == "terminal":
                old_token_hash = current.get("token_sha256")
                await self._redis.delete(key)
                if old_token_hash:
                    await self._redis.delete(self.keys.token_hash(str(old_token_hash)))
                await self.delete_checkpoint(session_id)
                await self.clear_recoverable(session_id)
                existing = None
            else:
                if (
                    current.get("call_attempt_id") != call_attempt_id
                    or current.get("payload_hash") != payload_hash
                ):
                    raise ValueError("Prepared interview payload or active attempt conflict")
                await self._redis.expire(key, SESSION_RETENTION_SECONDS)
                stored_expiry = int(current["expires_at"])
                if stored_expiry <= int(time.time()):
                    stored_expiry = expires_at_epoch_seconds
                    current["expires_at"] = stored_expiry
                    await self._redis.set(
                        key,
                        json.dumps(
                            current,
                            ensure_ascii=False,
                            sort_keys=True,
                            separators=(",", ":"),
                        ),
                        ex=SESSION_RETENTION_SECONDS,
                    )
                await self._redis.set(
                    self.keys.token_hash(token_sha256),
                    json.dumps(
                        {
                            "session_id": session_id,
                            "call_attempt_id": call_attempt_id,
                            "tenant_id": tenant_id,
                            "expires_at": stored_expiry,
                        },
                        separators=(",", ":"),
                    ),
                    ex=max(1, stored_expiry - int(time.time())),
                )
                return RuntimePreparation(
                    created=False,
                    payload_hash=payload_hash,
                    expires_at_epoch_seconds=stored_expiry,
                )
        document = {
            "session_id": session_id,
            "call_attempt_id": call_attempt_id,
            "tenant_id": tenant_id,
            "payload_hash": payload_hash,
            "token_sha256": token_sha256,
            "expires_at": expires_at_epoch_seconds,
            "status": "prepared",
            "claimed_call_sid": "",
            "payload": payload,
        }
        created = await self._redis.set(
            key,
            json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
            ex=SESSION_RETENTION_SECONDS,
            nx=True,
        )
        if not created:
            return await self.prepare_runtime_session(
                session_id=session_id,
                call_attempt_id=call_attempt_id,
                tenant_id=tenant_id,
                payload=payload,
                payload_hash=payload_hash,
                token_sha256=token_sha256,
                expires_at_epoch_seconds=expires_at_epoch_seconds,
            )
        token_metadata = {
            "session_id": session_id,
            "call_attempt_id": call_attempt_id,
            "tenant_id": tenant_id,
            "expires_at": expires_at_epoch_seconds,
        }
        ttl = max(1, expires_at_epoch_seconds - int(time.time()))
        await self._redis.set(
            self.keys.token_hash(token_sha256),
            json.dumps(token_metadata, separators=(",", ":")),
            ex=ttl,
        )
        return RuntimePreparation(True, payload_hash, expires_at_epoch_seconds)

    async def claim_runtime_token(
        self,
        *,
        token: str,
        call_sid: str,
        global_limit: int,
        tenant_limit: int,
        lease_seconds: int,
    ) -> RuntimeTokenClaim:
        token_sha256 = hashlib.sha256(token.encode("utf-8")).hexdigest()
        token_key = self.keys.token_hash(token_sha256)
        raw = await self._redis.get(token_key)
        if raw is None:
            raise ValueError("Runtime token is invalid or expired")
        metadata = json.loads(_text(raw))
        session_id = str(metadata["session_id"])
        tenant_id = str(metadata["tenant_id"])
        result = await cast(
            Awaitable[Any],
            self._redis.eval(
                _CLAIM_TOKEN,
                5,
                token_key,
                self.keys.session(session_id),
                self.keys.global_concurrency(),
                self.keys.tenant_concurrency(tenant_id),
                self.keys.recovery_index(),
                int(time.time()),
                call_sid,
                lease_seconds,
                global_limit,
                tenant_limit,
                SESSION_RETENTION_SECONDS,
            ),
        )
        code = int(result[0])
        if code == -1:
            raise ValueError("Runtime token is invalid or expired")
        if code == -2:
            raise ValueError("Runtime token was already claimed by another call")
        if code in {-3, -4}:
            raise RuntimeError("Interview concurrency limit is exhausted")
        return RuntimeTokenClaim(
            session_id=session_id,
            call_attempt_id=str(metadata["call_attempt_id"]),
            tenant_id=tenant_id,
            same_call_replay=code == 2,
        )

    async def prepared_session(self, session_id: str) -> dict[str, Any] | None:
        raw = await self._redis.get(self.keys.session(session_id))
        return None if raw is None else cast(dict[str, Any], json.loads(_text(raw)))

    async def renew_runtime_session(
        self, session_id: str, call_sid: str, *, lease_seconds: int
    ) -> bool:
        session = await self.prepared_session(session_id)
        if session is None:
            return False
        tenant_id = str(session["tenant_id"])
        result = await cast(
            Awaitable[Any],
            self._redis.eval(
                _RENEW_SESSION,
                4,
                self.keys.session(session_id),
                self.keys.global_concurrency(),
                self.keys.tenant_concurrency(tenant_id),
                self.keys.recovery_index(),
                call_sid,
                int(time.time()),
                lease_seconds,
                SESSION_RETENTION_SECONDS,
            ),
        )
        return bool(result)

    async def cancel_runtime_session(self, session_id: str, call_attempt_id: str) -> bool:
        session = await self.prepared_session(session_id)
        if session is None:
            return False
        if session.get("call_attempt_id") != call_attempt_id:
            raise ValueError("Interview cancellation attempt mismatch")
        if session.get("status") == "terminal":
            return False
        session["status"] = "terminal"
        await self._redis.set(
            self.keys.session(session_id),
            json.dumps(session, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
            ex=SESSION_RETENTION_SECONDS,
        )
        await self._redis.delete(self.keys.token_hash(str(session["token_sha256"])))
        await self._redis.zrem(self.keys.global_concurrency(), call_attempt_id)
        await self._redis.zrem(
            self.keys.tenant_concurrency(str(session["tenant_id"])), call_attempt_id
        )
        await self.delete_checkpoint(session_id)
        await self.clear_recoverable(session_id)
        return True

    async def release_runtime_claim(self, session_id: str, call_attempt_id: str) -> None:
        session = await self.prepared_session(session_id)
        if session is None or session.get("call_attempt_id") != call_attempt_id:
            return
        await self._redis.zrem(self.keys.global_concurrency(), call_attempt_id)
        await self._redis.zrem(
            self.keys.tenant_concurrency(str(session["tenant_id"])), call_attempt_id
        )

    async def terminalize_runtime_session(
        self, session_id: str, call_attempt_id: str, call_sid: str
    ) -> bool:
        session = await self.prepared_session(session_id)
        if session is None:
            return False
        result = await cast(
            Awaitable[Any],
            self._redis.eval(
                _TERMINALIZE_SESSION,
                6,
                self.keys.session(session_id),
                self.keys.token_hash(str(session.get("token_sha256", ""))),
                self.keys.global_concurrency(),
                self.keys.tenant_concurrency(str(session["tenant_id"])),
                self.keys.recovery_index(),
                self.keys.checkpoint(session_id),
                call_attempt_id,
                call_sid,
                SESSION_RETENTION_SECONDS,
            ),
        )
        if int(result) < 0:
            raise ValueError("Interview terminalization binding mismatch")
        return bool(result)

    async def claim_resume_request(self, analysis_id: str, payload_hash: str) -> bool:
        key = self.keys.resume(analysis_id)
        created = bool(
            await cast(Awaitable[Any], self._redis.hsetnx(key, "payload_hash", payload_hash))
        )
        existing = await cast(Awaitable[Any], self._redis.hget(key, "payload_hash"))
        if existing is None or _text(existing) != payload_hash:
            raise ValueError("Resume-analysis request payload hash mismatch")
        await cast(Awaitable[Any], self._redis.hsetnx(key, "attempts", "0"))
        await cast(Awaitable[Any], self._redis.expire(key, RESUME_RETENTION_SECONDS))
        return created

    async def increment_resume_attempts(self, analysis_id: str) -> int:
        key = self.keys.resume(analysis_id)
        value = await cast(Awaitable[Any], self._redis.hincrby(key, "attempts", 1))
        await cast(Awaitable[Any], self._redis.expire(key, RESUME_RETENTION_SECONDS))
        return int(value)

    async def store_pending_outcome(
        self, analysis_id: str, payload: bytes, *, ttl_seconds: int
    ) -> None:
        await self._redis.set(self.keys.pending_outcome(analysis_id), payload, ex=ttl_seconds)

    async def pending_outcome(self, analysis_id: str) -> bytes | None:
        value = await self._redis.get(self.keys.pending_outcome(analysis_id))
        return cast(bytes | None, value)

    async def delete_pending_outcome(self, analysis_id: str) -> None:
        await self._redis.delete(self.keys.pending_outcome(analysis_id))

    async def acquire_lease(self, session_id: str) -> str | None:
        token = str(uuid4())
        acquired = await self._redis.set(
            self.keys.lease(session_id), token.encode("utf-8"), ex=LEASE_SECONDS, nx=True
        )
        return token if acquired else None

    async def renew_lease(self, session_id: str, lease_token: str) -> bool:
        result = await cast(
            Awaitable[Any],
            self._redis.eval(
                _RENEW_LEASE,
                1,
                self.keys.lease(session_id),
                lease_token,
                LEASE_SECONDS,
            ),
        )
        return bool(result)

    async def release_lease(self, session_id: str, lease_token: str) -> None:
        await cast(
            Awaitable[Any],
            self._redis.eval(_RELEASE_LEASE, 1, self.keys.lease(session_id), lease_token),
        )

    async def mark_confirmed_publication(self, event_id: str) -> None:
        await self._redis.set(self.keys.event(event_id), b"confirmed", ex=EVENT_RETENTION_SECONDS)

    async def publication_confirmed(self, event_id: str) -> bool:
        return bool(await self._redis.exists(self.keys.event(event_id)))

def _text(value: object) -> str:
    if isinstance(value, bytes):
        return value.decode("utf-8")
    return cast(str, value)

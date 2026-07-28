from __future__ import annotations

import hashlib
import os
import time
from urllib.parse import urlsplit, urlunsplit
from uuid import UUID, uuid4

import pytest
import redis.asyncio as redis

from app.contracts.ai_interview_v1 import interview_event_id
from app.modules.interview.internal.redis_state import (
    EVENT_RETENTION_SECONDS,
    LEASE_SECONDS,
    SESSION_RETENTION_SECONDS,
    TOKEN_TTL_SECONDS,
    InterviewRedisState,
)


def _database_13_url(url: str) -> str:
    parts = urlsplit(url)
    return urlunsplit((parts.scheme, parts.netloc, "/13", parts.query, parts.fragment))


@pytest.mark.asyncio
@pytest.mark.skipif(not os.getenv("REDIS_TEST_URL"), reason="REDIS_TEST_URL is not set")
async def test_interview_state_ttls_idempotency_leases_and_publication_markers() -> None:
    client = redis.from_url(_database_13_url(os.environ["REDIS_TEST_URL"]), decode_responses=False)
    prefix = f"ccn:test:{uuid4().hex}"
    state = InterviewRedisState(client, prefix=prefix)
    session_id = str(uuid4())
    try:
        first = await state.store_prepared_session(session_id, {"snapshot": "v1"})
        replay = await state.store_prepared_session(session_id, {"snapshot": "v1"})
        assert first.created is True
        assert replay.created is False
        assert await client.ttl(state.keys.session(session_id)) <= SESSION_RETENTION_SECONDS
        with pytest.raises(ValueError, match="hash mismatch"):
            await state.store_prepared_session(session_id, {"snapshot": "v2"})

        token = "opaque-token"
        await state.store_runtime_token(token, session_id)
        assert await client.ttl(state.keys.token(token)) <= TOKEN_TTL_SECONDS
        lease = await state.acquire_lease(session_id)
        assert lease is not None
        assert await client.ttl(state.keys.lease(session_id)) <= LEASE_SECONDS
        assert await state.renew_lease(session_id, lease)
        await state.release_lease(session_id, lease)

        analysis_id = str(uuid4())
        assert await state.claim_resume_request(analysis_id, "a" * 64)
        assert not await state.claim_resume_request(analysis_id, "a" * 64)
        with pytest.raises(ValueError, match="hash mismatch"):
            await state.claim_resume_request(analysis_id, "b" * 64)
        assert await state.increment_resume_attempts(analysis_id) == 1
        await state.store_pending_outcome(analysis_id, b'{"redacted":"result"}', ttl_seconds=900)
        assert await state.pending_outcome(analysis_id) == b'{"redacted":"result"}'
        assert await client.ttl(state.keys.pending_outcome(analysis_id)) <= 900
        await state.delete_pending_outcome(analysis_id)
        assert await state.pending_outcome(analysis_id) is None

        event_id = str(uuid4())
        await state.mark_confirmed_publication(event_id)
        assert await state.publication_confirmed(event_id)
        assert await client.ttl(state.keys.event(event_id)) <= EVENT_RETENTION_SECONDS

        checkpoint_session = str(uuid4())
        revision = await state.cas_checkpoint(
            checkpoint_session,
            expected_revision=0,
            phase="LISTENING",
            runtime_state={"engine": {"next_turn_sequence": 1}},
            call_sid="CA" + "3" * 32,
        )
        assert revision == 1
        checkpoint_event = str(uuid4())
        revision = await state.stage_checkpoint_event(
            checkpoint_session,
            expected_revision=revision,
            phase="CANDIDATE_PUBLICATION",
            commit_phase="CANDIDATE_EVALUATION",
            current_runtime_state={"engine": {"next_turn_sequence": 1}},
            next_runtime_state={"engine": {"next_turn_sequence": 2}},
            event_id=checkpoint_event,
            routing_key="interview.turn.finalized",
            payload=b'{"schema_version":"1.1"}',
            call_sid="CA" + "3" * 32,
        )
        await state.mark_confirmed_publication(checkpoint_event)
        revision = await state.commit_checkpoint_event(
            checkpoint_session, expected_revision=revision, event_id=checkpoint_event
        )
        checkpoint = await state.load_checkpoint(checkpoint_session)
        assert revision == 3 and checkpoint is not None
        assert checkpoint.pending_event is None
        assert checkpoint.runtime_state["engine"]["next_turn_sequence"] == 2
        with pytest.raises(ValueError, match="revision conflict"):
            await state.cas_checkpoint(
                checkpoint_session,
                expected_revision=1,
                phase="LISTENING",
                runtime_state={},
            )
        await state.mark_recoverable(checkpoint_session, recover_at_epoch_seconds=1)
        assert checkpoint_session in await state.due_recoveries(now_epoch_seconds=2)
        await state.delete_checkpoint(checkpoint_session)

        runtime_token = "deterministic-runtime-token"
        runtime_session_id = str(uuid4())
        token_hash = hashlib.sha256(runtime_token.encode()).hexdigest()
        runtime_attempt = str(uuid4())
        runtime_tenant = str(uuid4())
        expires_at = int(time.time()) + 900
        prepared = await state.prepare_runtime_session(
            session_id=runtime_session_id,
            call_attempt_id=runtime_attempt,
            tenant_id=runtime_tenant,
            payload={"snapshotVersion": "interview-session-v1"},
            payload_hash="c" * 64,
            token_sha256=token_hash,
            expires_at_epoch_seconds=expires_at,
        )
        assert prepared.created
        assert runtime_token not in state.keys.token_hash(token_hash)
        claim = await state.claim_runtime_token(
            token=runtime_token,
            call_sid="CA" + "1" * 32,
            global_limit=1,
            tenant_limit=1,
            lease_seconds=30,
        )
        replay = await state.claim_runtime_token(
            token=runtime_token,
            call_sid="CA" + "1" * 32,
            global_limit=1,
            tenant_limit=1,
            lease_seconds=30,
        )
        assert claim.same_call_replay is False
        assert replay.same_call_replay is True
        claimed_deadline = await client.zscore(
            state.keys.recovery_index(), runtime_session_id
        )
        assert claimed_deadline is not None
        with pytest.raises(ValueError, match="another call"):
            await state.claim_runtime_token(
                token=runtime_token,
                call_sid="CA" + "2" * 32,
                global_limit=1,
                tenant_limit=1,
                lease_seconds=30,
            )
        assert await state.renew_runtime_session(
            runtime_session_id, "CA" + "1" * 32, lease_seconds=60
        )
        renewed_deadline = await client.zscore(
            state.keys.recovery_index(), runtime_session_id
        )
        assert renewed_deadline is not None and renewed_deadline > claimed_deadline
        runtime_marker = str(uuid4())
        await state.mark_confirmed_publication(runtime_marker)
        await state.cas_checkpoint(
            runtime_session_id,
            expected_revision=0,
            phase="TERMINAL_COMPLETE",
            runtime_state={"engine": {"next_turn_sequence": 1}},
            call_sid="CA" + "1" * 32,
        )
        with pytest.raises(ValueError, match="binding mismatch"):
            await state.terminalize_runtime_session(
                runtime_session_id, runtime_attempt, "CA" + "9" * 32
            )
        assert await client.exists(state.keys.checkpoint(runtime_session_id))
        assert await client.exists(state.keys.token_hash(token_hash))
        assert await client.zscore(state.keys.recovery_index(), runtime_session_id) is not None
        assert await state.publication_confirmed(runtime_marker)
        assert await state.terminalize_runtime_session(
            runtime_session_id, runtime_attempt, "CA" + "1" * 32
        )
        terminal = await state.prepared_session(runtime_session_id)
        assert terminal is not None and terminal["status"] == "terminal"
        assert not await client.exists(state.keys.checkpoint(runtime_session_id))
        assert not await client.zscore(state.keys.global_concurrency(), runtime_attempt)
        assert not await client.zscore(
            state.keys.tenant_concurrency(runtime_tenant), runtime_attempt
        )
        assert await client.zscore(state.keys.recovery_index(), runtime_session_id) is None
        assert await state.publication_confirmed(runtime_marker)
        assert not await state.cancel_runtime_session(runtime_session_id, runtime_attempt)
        with pytest.raises(ValueError, match="invalid or expired"):
            await state.claim_runtime_token(
                token=runtime_token,
                call_sid="CA" + "1" * 32,
                global_limit=1,
                tenant_limit=1,
                lease_seconds=30,
            )

        completed_marker = str(
            interview_event_id(
                "interview.session.completed", UUID(runtime_session_id), "completed:v1.1"
            )
        )
        await state.mark_confirmed_publication(completed_marker)
        await state.cas_checkpoint(
            runtime_session_id,
            expected_revision=0,
            phase="TERMINAL_COMPLETE",
            runtime_state={"engine": {"next_turn_sequence": 1}},
            call_sid="CA" + "1" * 32,
        )
        await state.mark_recoverable(runtime_session_id, recover_at_epoch_seconds=1)
        replacement = await state.prepare_runtime_session(
            session_id=runtime_session_id,
            call_attempt_id=str(uuid4()),
            tenant_id=runtime_tenant,
            payload={"snapshotVersion": "interview-session-v1", "replacement": True},
            payload_hash="d" * 64,
            token_sha256="e" * 64,
            expires_at_epoch_seconds=int(time.time()) + 900,
        )
        assert replacement.created
        assert not await client.exists(state.keys.checkpoint(runtime_session_id))
        assert await client.zscore(state.keys.recovery_index(), runtime_session_id) is None
        assert await state.publication_confirmed(completed_marker)
        assert await state.publication_confirmed(runtime_marker)
    finally:
        keys = [key async for key in client.scan_iter(match=f"{prefix}:*")]
        if keys:
            await client.delete(*keys)
        await client.aclose()

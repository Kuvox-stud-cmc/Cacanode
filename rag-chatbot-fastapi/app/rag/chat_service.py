from __future__ import annotations

import json
import logging
import re
import time
from collections.abc import Sequence
from typing import Any, Protocol

from app.core.config import Settings
from app.core.metrics import AI_RAG_ANSWER_SECONDS
from app.rag.calculation import SpreadsheetCalculationCoordinator
from app.rag.errors import ChatModelTimeoutError, ChatSessionNotFoundError
from app.rag.models import AssistantMessage, ChatMessage, ChatSession, Citation, RetrievedChunk
from app.rag.sessions import ChatSessionStore

logger = logging.getLogger(__name__)

NO_INFORMATION_RESPONSE = (
    "Mình không tìm thấy thông tin phù hợp trong tài liệu đã tải lên để trả lời câu hỏi này."
)


class QueryEmbedder(Protocol):
    async def embed_query(self, text: str) -> list[float]: ...


class VectorRetriever(Protocol):
    async def retrieve(
        self,
        *,
        tenant_id: str,
        knowledge_base_id: str,
        query_vector: Sequence[float],
        limit: int,
        score_threshold: float,
        document_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]: ...


class ChatModel(Protocol):
    async def complete(self, messages: Sequence[dict[str, object]]) -> str: ...


class RagChatService:
    def __init__(
        self,
        *,
        settings: Settings,
        sessions: ChatSessionStore,
        embedder: QueryEmbedder,
        retriever: VectorRetriever,
        chat_model: ChatModel,
        calculations: SpreadsheetCalculationCoordinator | None = None,
    ):
        self._settings = settings
        self._sessions = sessions
        self._embedder = embedder
        self._retriever = retriever
        self._chat_model = chat_model
        self._calculations = calculations

    def create_session(
        self,
        *,
        tenant_id: str,
        user_id: str | None,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
        channel: str = "EMPLOYEE_PLAYGROUND",
        external_user_id: str | None = None,
        customer_name: str | None = None,
        customer_email: str | None = None,
        customer_metadata: dict[str, Any] | None = None,
        integration_token_id: str | None = None,
    ) -> ChatSession:
        return self._sessions.create(
            tenant_id=tenant_id,
            user_id=user_id,
            chatbot_id=chatbot_id,
            knowledge_base_id=knowledge_base_id,
            locale=locale,
            channel=channel,
            external_user_id=external_user_id,
            customer_name=customer_name,
            customer_email=customer_email,
            customer_metadata=customer_metadata,
            integration_token_id=integration_token_id,
        )

    def list_messages(
        self,
        *,
        tenant_id: str,
        session_id: str,
        limit: int = 50,
        after: int | None = None,
        integration_token_id: str | None = None,
        user_id: str | None = None,
    ) -> list[ChatMessage]:
        session = self._sessions.get_for_tenant(session_id, tenant_id)
        if session is None:
            raise ChatSessionNotFoundError(session_id)
        if (
            integration_token_id is not None
            and session.integration_token_id != integration_token_id
        ):
            raise ChatSessionNotFoundError(session_id)
        if user_id is not None and (
            session.channel != "EMPLOYEE_PLAYGROUND" or session.user_id != user_id
        ):
            raise ChatSessionNotFoundError(session_id)
        return self._sessions.list_messages(
            session_id=session_id,
            tenant_id=tenant_id,
            limit=limit,
            after=after,
        )

    def close_session(
        self,
        *,
        tenant_id: str,
        session_id: str,
        integration_token_id: str | None = None,
        user_id: str | None = None,
    ) -> None:
        session = self._sessions.get_for_tenant(session_id, tenant_id)
        if session is None or (
            integration_token_id is not None
            and session.integration_token_id != integration_token_id
        ):
            raise ChatSessionNotFoundError(session_id)
        if user_id is not None and (
            session.channel != "EMPLOYEE_PLAYGROUND" or session.user_id != user_id
        ):
            raise ChatSessionNotFoundError(session_id)
        if not self._sessions.close_for_tenant(session_id, tenant_id):
            raise ChatSessionNotFoundError(session_id)

    def list_playground_sessions(
        self, *, tenant_id: str, user_id: str, limit: int, offset: int
    ) -> list[dict[str, Any]]:
        return self._sessions.list_playground_sessions(
            tenant_id=tenant_id, user_id=user_id, limit=limit, offset=offset
        )

    def hide_playground_session(self, *, tenant_id: str, user_id: str, session_id: str) -> None:
        if not self._sessions.hide_playground_session(
            tenant_id=tenant_id, user_id=user_id, session_id=session_id
        ):
            raise ChatSessionNotFoundError(session_id)

    def list_external_conversations(
        self,
        *,
        tenant_id: str,
        status: str | None,
        limit: int,
        offset: int,
    ) -> list[dict[str, Any]]:
        method = getattr(self._sessions, "list_external_conversations", None)
        if method is None:
            return []
        return method(tenant_id=tenant_id, status=status, limit=limit, offset=offset)

    def get_external_conversation(
        self, *, tenant_id: str, session_id: str
    ) -> tuple[dict[str, Any], list[ChatMessage]] | None:
        method = getattr(self._sessions, "get_external_conversation", None)
        if method is None:
            return None
        return method(tenant_id=tenant_id, session_id=session_id)

    async def submit_message(
        self,
        *,
        tenant_id: str,
        session_id: str,
        content: str,
        integration_token_id: str | None = None,
        user_id: str | None = None,
    ) -> AssistantMessage:
        total_started_at = time.perf_counter()
        outcome = "success"
        embedding_seconds = 0.0
        retrieval_seconds = 0.0
        llm_seconds = 0.0
        chunk_count = 0
        llm_provider = str(getattr(self._chat_model, "provider", self._settings.LLM_PROVIDER))
        llm_model = str(
            getattr(
                self._chat_model,
                "model",
                self._settings.OPENAI_MODEL
                if self._settings.LLM_PROVIDER == "openai"
                else self._settings.LLM_MODEL_ID,
            )
        )

        try:
            session = self._sessions.get_for_tenant(session_id, tenant_id)
            if session is None:
                outcome = "error"
                raise ChatSessionNotFoundError(session_id)
            if (
                integration_token_id is not None
                and session.integration_token_id != integration_token_id
            ):
                outcome = "error"
                raise ChatSessionNotFoundError(session_id)
            if user_id is not None and (
                session.channel != "EMPLOYEE_PLAYGROUND" or session.user_id != user_id
            ):
                outcome = "error"
                raise ChatSessionNotFoundError(session_id)

            self._sessions.consume_message_quota(tenant_id)
            self._sessions.add_user_message(session.id, content)

            embedding_started_at = time.perf_counter()
            embedding_outcome = "success"
            try:
                query_vector = await self._embedder.embed_query(content)
            except Exception:
                embedding_outcome = "error"
                outcome = "error"
                raise
            finally:
                embedding_seconds = time.perf_counter() - embedding_started_at
                AI_RAG_ANSWER_SECONDS.labels(
                    stage="embedding",
                    provider=llm_provider,
                    outcome=embedding_outcome,
                ).observe(embedding_seconds)

            retrieval_started_at = time.perf_counter()
            retrieval_outcome = "success"
            try:
                set_query = getattr(self._retriever, "set_query", None)
                if set_query is not None:
                    set_query(content)
                if session.channel in {"WIDGET", "CUSTOM_API"}:
                    visible_ids = self._sessions.customer_visible_document_ids(
                        tenant_id=tenant_id, knowledge_base_id=session.knowledge_base_id
                    )
                    chunks = (
                        []
                        if not visible_ids
                        else await self._retriever.retrieve(
                            tenant_id=tenant_id,
                            knowledge_base_id=session.knowledge_base_id,
                            query_vector=query_vector,
                            limit=self._settings.TEXT_TOP_K,
                            score_threshold=self._settings.MIN_RETRIEVAL_CONFIDENCE,
                            document_ids=visible_ids,
                        )
                    )
                else:
                    chunks = await self._retriever.retrieve(
                        tenant_id=tenant_id,
                        knowledge_base_id=session.knowledge_base_id,
                        query_vector=query_vector,
                        limit=self._settings.TEXT_TOP_K,
                        score_threshold=self._settings.MIN_RETRIEVAL_CONFIDENCE,
                    )
            except Exception:
                retrieval_outcome = "error"
                outcome = "error"
                raise
            finally:
                retrieval_seconds = time.perf_counter() - retrieval_started_at
                AI_RAG_ANSWER_SECONDS.labels(
                    stage="retrieval",
                    provider=llm_provider,
                    outcome=retrieval_outcome,
                ).observe(retrieval_seconds)

            selected = chunks[: min(self._settings.FINAL_CONTEXT_TOP_K, 5)]
            calculation_text: str | None = None
            if self._calculations is not None:
                calculation = await self._calculations.prepare(
                    tenant_id=tenant_id,
                    knowledge_base_id=session.knowledge_base_id,
                    question=content,
                    chunks=chunks,
                )
                if calculation is not None:
                    if calculation.clarification:
                        message = AssistantMessage(
                            role="assistant", content=calculation.clarification
                        )
                        self._sessions.add_assistant_message(session.id, message)
                        return message
                    calculation_text = calculation.text
            chunk_count = len(selected)
            is_external = session.channel in {"WIDGET", "CUSTOM_API"}
            if not selected and not is_external:
                message = AssistantMessage(role="assistant", content=NO_INFORMATION_RESPONSE)
                self._sessions.add_assistant_message(session.id, message)
                return message

            citations = self._citations(selected)
            llm_started_at = time.perf_counter()
            llm_outcome = "success"
            try:
                raw_answer = (
                    await self._chat_model.complete(
                        self._external_prompt_messages(
                            question=content,
                            locale=session.locale,
                            chunks=selected,
                            citations=citations,
                            history=self._sessions.list_messages(
                                session_id=session.id,
                                tenant_id=tenant_id,
                                limit=20,
                            ),
                            calculation_context=calculation_text,
                        )
                        if is_external
                        else self._prompt_messages(
                            question=content,
                            locale=session.locale,
                            chunks=selected,
                            citations=citations,
                            calculation_context=calculation_text,
                        )
                    )
                ).strip()
            except ChatModelTimeoutError:
                llm_outcome = "timeout"
                outcome = "timeout"
                raise
            except Exception:
                llm_outcome = "error"
                outcome = "error"
                raise
            finally:
                llm_seconds = time.perf_counter() - llm_started_at
                AI_RAG_ANSWER_SECONDS.labels(
                    stage="llm",
                    provider=llm_provider,
                    outcome=llm_outcome,
                ).observe(llm_seconds)

            answer, action = (
                self._parse_external_answer(raw_answer) if is_external else (raw_answer, None)
            )
            message = AssistantMessage(
                role="assistant", content=answer, citations=citations, action=action
            )
            self._sessions.add_assistant_message(session.id, message)
            return message
        except ChatModelTimeoutError:
            outcome = "timeout"
            raise
        except Exception:
            if outcome == "success":
                outcome = "error"
            raise
        finally:
            total_seconds = time.perf_counter() - total_started_at
            AI_RAG_ANSWER_SECONDS.labels(
                stage="total",
                provider=llm_provider,
                outcome=outcome,
            ).observe(total_seconds)
            logger.info(
                "rag_chat_request tenant_id=%s session_id=%s embedding_ms=%.2f "
                "retrieval_ms=%.2f llm_ms=%.2f total_ms=%.2f llm_provider=%s "
                "llm_model=%s chunk_count=%s outcome=%s",
                tenant_id,
                session_id,
                embedding_seconds * 1000,
                retrieval_seconds * 1000,
                llm_seconds * 1000,
                total_seconds * 1000,
                llm_provider,
                llm_model,
                chunk_count,
                outcome,
                extra={
                    "tenant_id": tenant_id,
                    "session_id": session_id,
                    "embedding_ms": embedding_seconds * 1000,
                    "retrieval_ms": retrieval_seconds * 1000,
                    "llm_ms": llm_seconds * 1000,
                    "total_ms": total_seconds * 1000,
                    "llm_provider": llm_provider,
                    "llm_model": llm_model,
                    "chunk_count": chunk_count,
                    "outcome": outcome,
                },
            )

    def _prompt_messages(
        self,
        *,
        question: str,
        locale: str,
        chunks: list[RetrievedChunk],
        citations: list[Citation],
        calculation_context: str | None = None,
    ) -> list[dict[str, object]]:
        sources = "\n\n".join(
            f"[{citation.id}] {chunk.source_name}"
            f"{', page ' + str(chunk.page_number) if chunk.page_number is not None else ''}, "
            f"chunk {chunk.chunk_index}\n{chunk.text}"
            for chunk, citation in zip(chunks, citations, strict=True)
        )
        return [
            {
                "role": "system",
                "content": (
                    "You answer only from the supplied sources. "
                    "If the sources do not contain the answer, say you do not know. "
                    "Cite factual claims with [S1], [S2], etc. "
                    "Keep the answer concise, with at most three short sentences. "
                    f"Respond in locale {locale}."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"Sources:\n{sources}\n\n"
                    f"{calculation_context + chr(10) + chr(10) if calculation_context else ''}"
                    f"Question:\n{question}"
                ),
            },
        ]

    def _external_prompt_messages(
        self,
        *,
        question: str,
        locale: str,
        chunks: list[RetrievedChunk],
        citations: list[Citation],
        history: list[ChatMessage],
        calculation_context: str | None = None,
    ) -> list[dict[str, object]]:
        sources = (
            "\n\n".join(
                f"[{citation.id}] {chunk.source_name}\n{chunk.text}"
                for chunk, citation in zip(chunks, citations, strict=True)
            )
            or "No relevant tenant sources were found."
        )
        transcript = "\n".join(f"{message.role}: {message.content}" for message in history[-20:])[
            -8000:
        ]
        return [
            {
                "role": "system",
                "content": (
                    "Return only valid JSON with keys answer and ticketDraft. "
                    "ticketDraft must be null unless the customer explicitly asks to create, open, "
                    "or submit a support ticket. For an explicit request, ticketDraft must contain "
                    "a concise title and a useful description generated from the conversation. "
                    "Do not say the ticket has been created; say a draft is ready for review. "
                    "For knowledge questions, answer only from supplied sources and cite claims "
                    "with "
                    "[S1], [S2], etc. If sources are insufficient, say so. "
                    f"Respond in locale {locale}."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"Conversation:\n{transcript}\n\nSources:\n{sources}\n\n"
                    f"{calculation_context + chr(10) + chr(10) if calculation_context else ''}"
                    f"Latest customer message:\n{question}\n\n"
                    'JSON shape: {"answer":"...","ticketDraft":null} or '
                    '{"answer":"...","ticketDraft":{"title":"...","description":"..."}}'
                ),
            },
        ]

    def _parse_external_answer(self, raw_answer: str) -> tuple[str, dict[str, Any] | None]:
        cleaned = raw_answer.strip()
        if cleaned.startswith("```"):
            cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", cleaned, flags=re.IGNORECASE)
        try:
            payload = json.loads(cleaned)
            answer = str(payload.get("answer", "")).strip()
            draft = payload.get("ticketDraft")
            if isinstance(draft, dict):
                title = str(draft.get("title", "Support request")).strip() or "Support request"
                description = str(draft.get("description", "")).strip()
                if description:
                    return answer or "A support ticket draft is ready for review.", {
                        "type": "ticket_draft",
                        "title": title[:255],
                        "description": description[:10000],
                    }
            return answer or NO_INFORMATION_RESPONSE, None
        except (json.JSONDecodeError, TypeError, ValueError):
            logger.warning("external_chat_structured_response_invalid")
            return raw_answer or NO_INFORMATION_RESPONSE, None

    def _citations(self, chunks: list[RetrievedChunk]) -> list[Citation]:
        return [
            Citation(
                id=f"S{index}",
                document_id=chunk.document_id,
                source_name=chunk.source_name,
                page_number=chunk.page_number,
                chunk_index=chunk.chunk_index,
                score=chunk.score,
                snippet=self._snippet(chunk.text),
                unit_id=chunk.unit_id,
                modality=chunk.modality,
                section_path=chunk.section_path,
                block_type=chunk.block_type,
                sheet_name=chunk.sheet_name,
                cell_range=chunk.cell_range,
                table_id=chunk.table_id,
            )
            for index, chunk in enumerate(chunks, start=1)
        ]

    def _snippet(self, text: str) -> str:
        normalized = " ".join(text.split())
        if len(normalized) <= 220:
            return normalized
        return f"{normalized[:217].rstrip()}..."

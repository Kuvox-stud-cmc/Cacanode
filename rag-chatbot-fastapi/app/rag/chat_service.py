from __future__ import annotations

import logging
import time
from collections.abc import Sequence
from typing import Protocol

from app.core.config import Settings
from app.core.metrics import AI_RAG_ANSWER_SECONDS
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
    ):
        self._settings = settings
        self._sessions = sessions
        self._embedder = embedder
        self._retriever = retriever
        self._chat_model = chat_model

    def create_session(
        self,
        *,
        tenant_id: str,
        user_id: str,
        chatbot_id: str,
        knowledge_base_id: str,
        locale: str,
    ) -> ChatSession:
        return self._sessions.create(
            tenant_id=tenant_id,
            user_id=user_id,
            chatbot_id=chatbot_id,
            knowledge_base_id=knowledge_base_id,
            locale=locale,
        )

    def list_messages(
        self,
        *,
        tenant_id: str,
        session_id: str,
        limit: int = 50,
        after: int | None = None,
    ) -> list[ChatMessage]:
        session = self._sessions.get_for_tenant(session_id, tenant_id)
        if session is None:
            raise ChatSessionNotFoundError(session_id)
        return self._sessions.list_messages(
            session_id=session_id,
            tenant_id=tenant_id,
            limit=limit,
            after=after,
        )

    def close_session(self, *, tenant_id: str, session_id: str) -> None:
        if not self._sessions.close_for_tenant(session_id, tenant_id):
            raise ChatSessionNotFoundError(session_id)

    async def submit_message(
        self,
        *,
        tenant_id: str,
        session_id: str,
        content: str,
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
            chunk_count = len(selected)
            if not selected:
                message = AssistantMessage(role="assistant", content=NO_INFORMATION_RESPONSE)
                self._sessions.add_assistant_message(session.id, message)
                return message

            citations = self._citations(selected)
            llm_started_at = time.perf_counter()
            llm_outcome = "success"
            try:
                answer = (
                    await self._chat_model.complete(
                        self._prompt_messages(
                            question=content,
                            locale=session.locale,
                            chunks=selected,
                            citations=citations,
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

            message = AssistantMessage(role="assistant", content=answer, citations=citations)
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
                "content": f"Sources:\n{sources}\n\nQuestion:\n{question}",
            },
        ]

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
            )
            for index, chunk in enumerate(chunks, start=1)
        ]

    def _snippet(self, text: str) -> str:
        normalized = " ".join(text.split())
        if len(normalized) <= 220:
            return normalized
        return f"{normalized[:217].rstrip()}..."

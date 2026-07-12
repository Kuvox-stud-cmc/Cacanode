from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from app.core.config import Settings
from app.rag.errors import ChatSessionNotFoundError
from app.rag.models import AssistantMessage, ChatSession, Citation, RetrievedChunk
from app.rag.sessions import InMemoryChatSessionStore

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
        sessions: InMemoryChatSessionStore,
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

    async def submit_message(
        self,
        *,
        tenant_id: str,
        session_id: str,
        content: str,
    ) -> AssistantMessage:
        session = self._sessions.get_for_tenant(session_id, tenant_id)
        if session is None:
            raise ChatSessionNotFoundError(session_id)

        self._sessions.add_user_message(session.id, content)
        query_vector = await self._embedder.embed_query(content)
        chunks = await self._retriever.retrieve(
            tenant_id=tenant_id,
            knowledge_base_id=session.knowledge_base_id,
            query_vector=query_vector,
            limit=self._settings.TEXT_TOP_K,
            score_threshold=self._settings.MIN_RETRIEVAL_CONFIDENCE,
        )
        selected = chunks[: min(self._settings.FINAL_CONTEXT_TOP_K, 5)]
        if not selected:
            message = AssistantMessage(role="assistant", content=NO_INFORMATION_RESPONSE)
            self._sessions.add_assistant_message(session.id, message)
            return message

        citations = self._citations(selected)
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
        message = AssistantMessage(role="assistant", content=answer, citations=citations)
        self._sessions.add_assistant_message(session.id, message)
        return message

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

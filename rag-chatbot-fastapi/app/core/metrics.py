from prometheus_client import Counter, Histogram

AI_EMBEDDING_SECONDS = Histogram(
    "cacanode_ai_embedding_seconds",
    "Time spent generating embeddings.",
    ["operation", "provider", "outcome"],
)

AI_RETRIEVAL_SECONDS = Histogram(
    "cacanode_ai_retrieval_seconds",
    "Time spent retrieving chunks from vector storage.",
    ["provider", "outcome"],
)

AI_CHAT_MODEL_SECONDS = Histogram(
    "cacanode_ai_chat_model_seconds",
    "Time spent generating chat model answers.",
    ["provider", "model", "outcome"],
)

AI_RAG_ANSWER_SECONDS = Histogram(
    "cacanode_ai_rag_answer_seconds",
    "Time spent in RAG answer stages.",
    ["stage", "provider", "outcome"],
)

AI_CHAT_MODEL_TIMEOUTS_TOTAL = Counter(
    "cacanode_ai_chat_model_timeouts_total",
    "Total chat model generation timeouts.",
    ["provider", "model"],
)

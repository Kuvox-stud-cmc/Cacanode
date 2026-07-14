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

AI_ROUTER_PROFILES_TOTAL = Counter(
    "cacanode_ai_router_profiles_total",
    "Retrieval queries routed to each deterministic profile.",
    ["profile"],
)

AI_RETRIEVAL_CHANNEL_SECONDS = Histogram(
    "cacanode_ai_retrieval_channel_seconds",
    "Time spent in each retrieval channel.",
    ["channel", "outcome"],
)

AI_RETRIEVAL_CHANNEL_RESULTS = Histogram(
    "cacanode_ai_retrieval_channel_results",
    "Candidate count returned by each retrieval channel.",
    ["channel"],
)

AI_FUSION_CANDIDATES = Histogram(
    "cacanode_ai_fusion_candidates",
    "Candidate count retained after weighted reciprocal-rank fusion.",
)

AI_RERANKER_SECONDS = Histogram(
    "cacanode_ai_reranker_seconds",
    "Time spent reranking fused retrieval candidates.",
    ["outcome"],
)

AI_RETRIEVAL_FALLBACKS_TOTAL = Counter(
    "cacanode_ai_retrieval_fallbacks_total",
    "Fail-open retrieval fallbacks by component.",
    ["component"],
)

AI_CONTEXT_UNITS = Histogram(
    "cacanode_ai_context_units",
    "Final cited context unit count.",
)

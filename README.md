# Cacanode

Cacanode is a proprietary, Vietnamese-first, multi-tenant SaaS platform for building document-grounded conversational applications with Graph Retrieval-Augmented Generation (GraphRAG).

The product exposes two chat delivery surfaces:

1. **Cacanode Chat API** — a headless JSON and Server-Sent Events (SSE) API for customers that build their own chatbot UI, mobile chat, support console, or conversational workflow.
2. **Cacanode Chat Widget** — a hosted, configurable JavaScript widget for customers that need a ready-to-embed website chatbot.

Both surfaces use the same tenant-isolated knowledge base, chatbot configuration, retrieval pipeline, conversation model, usage limits, and source-citation format.

| Property | Value |
|---|---|
| Product type | Hosted multi-tenant SaaS |
| Primary market | Vietnamese businesses and Vietnamese-language applications |
| Integration modes | Headless Chat API and hosted JavaScript widget |
| Streaming protocol | Server-Sent Events over HTTPS |
| Retrieval | Qdrant dense + BM25 sparse search, Kuzu graph evidence, and TEI reranking |
| Generative model | Self-hosted Gemma 4 instruction model |
| Text embedding | Self-hosted EmbeddingGemma |
| Model operation | Fully managed by Cacanode |
| Source-code license | Proprietary |

---

## Implementation Status

This repository provides a runnable vertical slice for document-grounded chat, ingestion, tenant isolation, citations, hybrid retrieval, and operational reindexing. Multimodal processing and several broader platform capabilities remain staged work.

Implemented foundations include:

- Next.js management-console shell and authentication client.
- Spring Boot identity, tenant, persistence, and versioned-route compatibility.
- Backend-authoritative Starter, Trial, Pro, and Enterprise entitlements with PayOS-hosted checkout, verified webhook activation, manual renewal, reconciliation, quota enforcement, and subscription lifecycle management.
- FastAPI chat/session contracts, grounded citations, document ingestion, structural chunking, dense/sparse/graph retrieval, reranking fallbacks, request IDs, health checks, and worker lifecycles.
- PostgreSQL, Redis, RabbitMQ, Qdrant, Kuzu storage, SeaweedFS, gateway, and application Compose definitions.
- Optional dedicated-worker and GPU model-serving profiles.

Image, audio, video, OCR ingestion, and several broader management APIs remain implementation work. Unimplemented paths return explicit errors rather than fabricated successful responses.

---

## Table of Contents

- [Product and Business Model](#product-and-business-model)
- [Implementation Status](#implementation-status)
- [Product Surfaces](#product-surfaces)
- [Functional Scope](#functional-scope)
- [System Architecture](#system-architecture)
- [Service Responsibilities](#service-responsibilities)
- [AI and Retrieval Stack](#ai-and-retrieval-stack)
- [LangChain Boundary](#langchain-boundary)
- [Supported Knowledge Sources](#supported-knowledge-sources)
- [Document and Media Ingestion](#document-and-media-ingestion)
- [GraphRAG Query Processing](#graphrag-query-processing)
- [Public Chat API](#public-chat-api)
- [Chat Widget](#chat-widget)
- [Management API](#management-api)
- [Billing, Subscriptions, and Quotas](#billing-subscriptions-and-quotas)
- [Data and Storage Contracts](#data-and-storage-contracts)
- [Vietnamese Model Adaptation](#vietnamese-model-adaptation)
- [Security and Tenant Isolation](#security-and-tenant-isolation)
- [Performance and Availability](#performance-and-availability)
- [Configuration](#configuration)
- [Local Deployment](#local-deployment)
- [Testing and Release Gates](#testing-and-release-gates)
- [License](#license)

---

## Product and Business Model

Cacanode is delivered as a hosted commercial service. Customers subscribe to use the management console, knowledge ingestion, managed AI inference, public Chat API, and hosted widget. Customers do not receive the Cacanode source code or direct access to internal model-serving infrastructure.

### Commercial offering

A subscription or trial grants access according to its plan entitlements:

- One or more tenant-scoped chatbots.
- One or more tenant-scoped knowledge bases.
- The headless Chat API.
- The hosted chat widget.
- Document and media ingestion.
- Conversation history and source citations.
- Integration credential management.
- Usage, quota, and operational status views.

Commercial entitlements are represented by tenant subscription records and projected onto the tenant runtime. Self-service Pro purchases use server-created PayOS payment links, and activation occurs only after a verified PayOS webhook is durably processed. Enterprise provisioning remains sales-led.

### Metered usage

The platform records usage for:

- Chat requests.
- Input and output tokens.
- Concurrent streams.
- Indexed source count.
- Uploaded and derived storage.
- Ingestion processing time.
- Vector and graph records.
- Audio and video processing duration.

The API and widget consume the same tenant message quota. A chat request is rejected with HTTP `429` and `MESSAGE_QUOTA_EXCEEDED` when the applicable billing-period allowance is exhausted.

### Managed AI operation

All generative, embedding, speech, OCR, image, and audio models are selected, hosted, versioned, and operated by Cacanode.

Customer integration credentials authenticate requests to Cacanode only. They are not credentials for Google, OpenAI, Hugging Face, or any other model provider.

### Customer control

Customers control:

- Their uploaded knowledge sources.
- Chatbot name, instructions, language, tone, fallback behavior, and citation visibility.
- Allowed website origins.
- Integration credentials.
- Widget appearance and behavior.
- Their own user interface when using the Chat API.
- Their own external user identifiers and application metadata.

Customers cannot select an arbitrary external model endpoint or submit external model-provider credentials.

---

## Product Surfaces

### 1. Headless Chat API

The Chat API has no fixed user-interface requirement. It supports custom:

- Web chat interfaces.
- React or React Native applications.
- Mobile customer-support experiences.
- Internal support consoles.
- Messaging gateways.
- Voice or multimodal applications that use chat as the reasoning layer.

The API supports:

- Session creation.
- Multi-turn messages.
- Streaming and non-streaming responses.
- Structured citations.
- External user IDs.
- Customer-defined metadata.
- Conversation history.
- Idempotent message submission.
- Rate-limit and usage metadata.

### 2. Hosted Chat Widget

The widget is an optional hosted UI that calls the same Chat API. It supports:

- A single-script website embed.
- Floating or inline display.
- Tenant branding.
- Vietnamese and English UI text.
- Streamed responses.
- Source citations.
- Session persistence.
- Origin restrictions.
- Responsive desktop and mobile layouts.

The widget is not required for API customers. Complete UI control is provided through the headless API.

### 3. Management Console

The management console provides:

- Tenant and user administration.
- Chatbot creation and configuration.
- Knowledge-base and source management.
- Ingestion status.
- Integration-key creation, rotation, and revocation.
- Widget configuration.
- Allowed-origin configuration.
- Usage and quota visibility.
- Audit and operational status views.

---

## Functional Scope

### Identity and tenancy

- Business registration creates a tenant and initial tenant administrator.
- Dashboard users authenticate with email and password.
- Access tokens use short-lived JWTs with refresh-token rotation.
- Supported roles are `PLATFORM_ADMIN`, `TENANT_ADMIN`, and `TENANT_MEMBER`.
- Every persistent object belongs to exactly one tenant unless it is a platform-level object.

### Chatbot management

A tenant may create multiple chatbots. Each chatbot references one knowledge base and contains:

- Display name.
- Default locale.
- Welcome message.
- Safe behavioral instructions.
- Response tone.
- Citation policy.
- General-knowledge policy.
- Retrieval settings.
- Allowed origins.
- Widget configuration.
- Active model configuration version.

### Knowledge management

- Sources are uploaded asynchronously.
- Raw sources are preserved in object storage.
- Derived text, tables, OCR, transcripts, keyframes, vectors, and graph facts retain source provenance.
- Deleting a source deletes all derived data for that source.
- Reindexing creates versioned derived artifacts and does not silently mix incompatible embeddings.

### Chat

- A chat session belongs to one tenant and one chatbot.
- A message belongs to one session.
- Retrieval is always scoped to the chatbot's tenant and knowledge base.
- Responses may be streamed through SSE or returned as completed JSON.
- Tenant-specific factual claims include machine-readable source citations.
- Conversation history is available only when permitted by the credential scope.

---

## System Architecture

```mermaid
flowchart TB
    CustomerApp[Customer Web, Mobile, or Support Application]
    Widget[Hosted Cacanode Widget]
    Dashboard[Management Console]

    CustomerApp --> Gateway[API Gateway / Nginx]
    Widget --> Gateway
    Dashboard --> Gateway

    Gateway --> Business[Spring Boot Business API]
    Gateway --> AI[FastAPI Chat and AI API]

    Business --> PostgreSQL[(PostgreSQL)]
    Business --> Redis[(Redis)]
    Business --> RabbitMQ[(RabbitMQ)]

    AI --> Orchestration[LangChain Orchestration Adapters]
    Orchestration --> LLM[Gemma 4 / vLLM]
    Orchestration --> TextEmbed[EmbeddingGemma Service]
    Orchestration --> Qdrant[(Qdrant)]
    Orchestration --> Graph[Kuzu Graph Service]

    RabbitMQ --> DocumentWorker[Document Worker]
    RabbitMQ --> OCRWorker[OCR Worker]
    RabbitMQ --> ASRWorker[Whisper Worker]
    RabbitMQ --> VisionWorker[CLIP Worker]
    RabbitMQ --> AudioWorker[CLAP Worker]
    RabbitMQ --> VideoWorker[Video / FFmpeg Worker]

    DocumentWorker --> SeaweedFS[(SeaweedFS)]
    OCRWorker --> SeaweedFS
    ASRWorker --> SeaweedFS
    VisionWorker --> SeaweedFS
    AudioWorker --> SeaweedFS
    VideoWorker --> SeaweedFS

    DocumentWorker --> TextEmbed
    OCRWorker --> TextEmbed
    ASRWorker --> TextEmbed
    VisionWorker --> Qdrant
    AudioWorker --> Qdrant
    TextEmbed --> Qdrant

    DocumentWorker --> LLM
    OCRWorker --> LLM
    ASRWorker --> LLM
    LLM --> Graph

    AI -. SSE .-> Gateway
    Gateway -. SSE .-> CustomerApp
    Gateway -. SSE .-> Widget
```

### External boundary

Only the gateway is public. The following services are private network services:

- Business API instances.
- AI API instances.
- Model servers.
- Embedding servers.
- Workers.
- PostgreSQL.
- Redis.
- RabbitMQ.
- Qdrant.
- Kuzu.
- SeaweedFS.

The public Chat API is a Cacanode API contract. The internal vLLM OpenAI-compatible endpoint is not exposed to tenants.

---

## Service Responsibilities

| Service | Responsibilities |
|---|---|
| API Gateway | TLS termination, routing, request IDs, CORS, response buffering controls, public rate limiting |
| Management Console | Tenant, chatbot, source, credential, widget, and usage administration |
| Hosted Widget | Browser chat UI, client-token bootstrap, SSE rendering, local session state |
| Spring Boot Business API | Identity, tenants, roles, chatbots, subscriptions, PayOS payment links, quota projections, credentials, audit records |
| FastAPI Chat and AI API | Public chat contract, SSE, retrieval, context assembly, GraphRAG execution |
| Gemma model service | Text generation, query routing, summarization, structured entity and relation extraction |
| Embedding service | EmbeddingGemma query and document vectors, batching, normalization, model versioning |
| Document worker | Parsing, structure extraction, chunking, table normalization, provenance |
| OCR worker | Vietnamese OCR for scans, images, and selected video frames |
| ASR worker | Timestamped Whisper transcription |
| Vision worker | CLIP image and video-keyframe embeddings |
| Audio worker | CLAP audio-window embeddings |
| Video worker | Media normalization, scene detection, keyframes, audio extraction, timestamp alignment |
| Graph service | Kuzu schema, writes, tenant-scoped traversals, evidence links |
| PostgreSQL | Business, identity, chat, source, usage, model, and audit metadata |
| Qdrant | Tenant-filtered vector retrieval across named vector spaces |
| SeaweedFS | Raw sources and derived binary artifacts |
| Redis | Cache, distributed locks, short-lived state, and rate-limit counters |
| RabbitMQ | Durable asynchronous ingestion and reindex jobs |

---

## AI and Retrieval Stack

The AI runtime uses self-hosted open-weight models and open-source infrastructure. Cacanode application source code remains proprietary.

| Capability | Component | Output |
|---|---|---|
| Response generation | Gemma 4 instruction model | Grounded text and structured output |
| Text semantic retrieval | EmbeddingGemma | Normalized text vectors |
| Image retrieval | CLIP-compatible model | Image and text vectors in a shared visual space |
| Non-speech audio retrieval | CLAP | Audio and text vectors in a shared acoustic space |
| Speech recognition | Whisper | Timestamped transcript text |
| OCR | PaddleOCR | Text blocks, bounding boxes, layout, and confidence |
| Document parsing | Docling and format-specific parsers | Structured sections, tables, images, and provenance |
| Media processing | FFmpeg | Normalized audio, scenes, frames, and timestamped segments |
| Vector database | Qdrant | Similarity, filtered, and fused retrieval |
| Graph database | Kuzu | Entity and relationship traversal |
| Model serving | vLLM | Internal generation endpoint |
| Orchestration | LangChain adapters | Model and retriever coordination |

### Model responsibility rules

- Gemma 4 is the generative model. It is not used as the primary document embedding model.
- EmbeddingGemma embeds extracted text. It does not parse PDF, DOCX, XLSX, audio, or video files.
- Whisper converts speech to text. The transcript is embedded by EmbeddingGemma.
- PaddleOCR extracts visible text. OCR output is embedded by EmbeddingGemma.
- CLIP embeds images and representative video frames.
- CLAP embeds non-speech audio and acoustic events.
- Vectors from different model families are stored and queried separately.
- Every generated artifact records its model, model version, adapter version, preprocessing version, and source hash.

### Video representation

A video is indexed as timestamped multimodal segments containing:

- Transcript text from Whisper.
- Visible text from PaddleOCR.
- Keyframe vectors from CLIP.
- Acoustic vectors from CLAP.
- Source metadata and time range.

OCR is not a video embedding. OCR produces text, and EmbeddingGemma converts that text into a semantic vector.

The current video representation is optimized for scene, speech, visible-text, and acoustic-event retrieval. It does not provide a dedicated long-range temporal-action vector.

---

## LangChain Boundary

LangChain is an internal orchestration dependency only.

### LangChain may

- Invoke internal model services.
- Construct prompts and model messages.
- Call Qdrant and Kuzu adapters.
- Route a query to text, graph, visual, or audio retrieval.
- Parse structured model output.
- Apply retries, timeouts, callbacks, and streaming adapters.
- Assemble retrieved context and citations.

### LangChain may not

- Own tenant authorization.
- Own the public API schema.
- Store business entities.
- Host or fine-tune models.
- Replace RabbitMQ workers.
- Parse every file format directly.
- Execute OCR, speech recognition, or media processing.
- Become the source of truth for sessions, messages, sources, or quotas.

Application code depends on project-owned ports and domain objects. LangChain-specific classes remain inside adapter packages.

```python
from typing import AsyncIterator, Protocol, Sequence


class ChatModelPort(Protocol):
    def stream(
        self,
        messages: Sequence[dict],
        *,
        model_version: str,
    ) -> AsyncIterator[str]: ...


class TextEmbeddingPort(Protocol):
    async def embed_documents(self, texts: Sequence[str]) -> list[list[float]]: ...
    async def embed_query(self, text: str) -> list[float]: ...


class GraphRetrieverPort(Protocol):
    async def retrieve(self, tenant_id: str, knowledge_base_id: str, query: str) -> list[dict]: ...
```

---

## Supported Knowledge Sources

| Source | Accepted formats | Processing |
|---|---|---|
| Digital documents | PDF, DOCX, TXT, Markdown, HTML | Parse, structure-aware chunk, text embedding, graph extraction |
| Spreadsheets | XLSX, CSV | Sheet/table normalization, row embeddings, deterministic calculations |
| Scanned documents | Scanned PDF, PNG, JPG, JPEG, TIFF | OCR, layout extraction, text embedding, optional image embedding |
| Images | PNG, JPG, JPEG, WEBP | OCR, CLIP embedding, optional generated description |
| Audio | WAV, MP3, M4A, FLAC, OGG | Whisper transcript, EmbeddingGemma transcript vectors, CLAP audio vectors |
| Video | MP4, WebM, MOV, MKV | FFmpeg, scene/keyframe extraction, Whisper, OCR, CLIP, CLAP |

Every upload is validated by extension, MIME type, file signature, configured size limit, and malware-scanning policy before processing.

The delivered digital-ingestion path accepts `.pdf`, `.docx`, `.txt`, `.md`, `.markdown`,
`.html`, `.htm`, `.xlsx`, and `.csv` files up to 20 MB. PDF input must contain extractable
text. Scanned-only PDFs, encrypted files, legacy `.doc`/`.xls` files, and malformed or unsafe
Office archives are rejected; image, audio, video, and OCR ingestion remain outside this path.

---

## Document and Media Ingestion

All ingestion is asynchronous.

### Source status lifecycle

```text
PENDING
  -> VALIDATING
  -> STORED
  -> PARSING
  -> INDEXING
  -> READY

Any processing state -> FAILED
READY -> DELETING -> DELETED
READY -> REINDEXING -> READY
```

Each status record contains:

- `tenant_id`
- `knowledge_base_id`
- `source_id`
- current stage
- progress percentage when measurable
- parser and model versions
- retry count
- safe error code and message
- created, started, completed, and updated timestamps

### Document pipeline

```mermaid
flowchart LR
    Upload[Upload] --> Validate[Validate]
    Validate --> Store[Store raw source]
    Store --> Parse[Parse structure]
    Parse --> Normalize[Normalize sections and tables]
    Normalize --> Chunk[Structure-aware chunks]
    Chunk --> Embed[EmbeddingGemma dense vectors]
    Chunk --> Sparse[FastEmbed Qdrant BM25]
    Chunk --> Extract[Gemma entity and relation extraction]
    Embed --> Vector[(Qdrant)]
    Sparse --> Vector
    Extract --> Graph[(Kuzu)]
```

A text chunk preserves:

- Source ID.
- File name.
- Section path.
- Heading context.
- Page number.
- Character or block offsets.
- Table headers when applicable.
- Content hash.
- Parser and chunker version.

Oversized prose and extracted page blocks target 800 characters and use a 120-character
overlap. Headings, lists, code, tables, spreadsheet rows, and sheet records use zero overlap and
split only on structural line or row boundaries. When a table spans multiple units, its header is
repeated so every unit remains independently understandable and citable.

### Spreadsheet pipeline

Spreadsheet ingestion creates two representations.

#### Semantic representation

Rows or logical records are serialized with workbook, sheet, header, cell range, and values.

```text
Workbook: pricing.xlsx
Sheet: Bảng giá
Range: A14:D14
Product: Gói doanh nghiệp
Monthly price: 1,500,000 VND
Maximum users: 50
Support level: Priority
```

The normalized record is embedded with EmbeddingGemma.

#### Deterministic calculation representation

Questions involving totals, averages, grouping, sorting, formulas, date ranges, or comparisons are executed by a constrained calculation service using the retrieved table. The generative model explains the result but does not perform unrestricted code execution.

Unrestricted model-generated Python, SQL, shell commands, and spreadsheet formulas are prohibited.

### Audio pipeline

```text
Audio
  -> normalize codec and sample rate
  -> speech segmentation
  -> Whisper transcript with timestamps
  -> EmbeddingGemma transcript vectors
  -> CLAP acoustic-window vectors
  -> optional graph extraction from transcript
```

### Video pipeline

```text
Video
  -> FFmpeg normalization
  -> scene and keyframe detection
  -> audio extraction
  -> Whisper transcript
  -> OCR on selected keyframes
  -> CLIP keyframe vectors
  -> CLAP audio-window vectors
  -> EmbeddingGemma vectors for transcript and OCR text
  -> timestamp-aligned multimodal records
```

Repeated subtitle or slide text is deduplicated across adjacent frames before indexing.

### Deletion contract

Deleting a source removes:

- The raw object.
- Derived pages, frames, audio, OCR, transcripts, and thumbnails.
- Qdrant points.
- Kuzu nodes and relationships whose evidence belongs only to that source.
- Source-specific cache entries.
- Pending jobs for obsolete source versions.

Deletion is tenant-scoped, idempotent, auditable, and safe to retry.

---

## GraphRAG Query Processing

```mermaid
flowchart TD
    Query[User query] --> Auth[Authenticate and derive tenant scope]
    Auth --> Policy[Load chatbot policy and conversation context]
    Policy --> Route[Classify query and retrieval modes]

    Route --> DenseQuery[EmbeddingGemma query]
    Route --> SparseQuery[BM25 sparse query]
    Route --> GraphQuery[Entity and relation query]

    DenseQuery --> DenseSearch[Qdrant dense top 40]
    SparseQuery --> SparseSearch[Qdrant sparse top 40]
    GraphQuery --> GraphSearch[Kuzu traversal]

    DenseSearch --> Fusion[Adaptive weighted RRF, k=30]
    SparseSearch --> Fusion
    GraphSearch --> Fusion

    Fusion --> Rerank[TEI bge-reranker-v2-m3]
    Rerank --> Primary[Select five diverse primary units]
    Primary --> Context[Add up to three eligible neighbors]
    Context --> Generate[Gemma 4 grounded generation]
    Generate --> Stream[JSON or SSE response]
```

### Retrieval rules

- Tenant and knowledge-base filters are generated from authenticated server context.
- A tenant identifier in request JSON is never treated as authorization.
- Each modality is searched in its own vector space.
- Routing precedence is calculation, relational, exact, then semantic; each profile selects its documented dense, sparse, and graph weights.
- Weighted reciprocal-rank fusion deduplicates by `(document_id, unit_id)` and retains 30 candidates for reranking.
- Reranking, graph search, and neighbor expansion fail open without discarding usable channel evidence.
- Context contains five primary units plus at most three prose/page neighbors; every unit has its own citation.
- Graph facts must retain evidence links to source units.
- Low-confidence retrieval returns an explicit unavailable answer rather than invented tenant facts.
- Uploaded content is untrusted data and cannot override system or chatbot policy.

### Grounding rules

- Tenant-specific factual claims must be supported by retrieved context.
- Citations are returned as structured objects, not only rendered text.
- General knowledge is disabled by default and may be enabled per chatbot.
- The model must distinguish unavailable information from system failure.
- Internal prompts, hidden instructions, and raw model traces are never returned through the public API.

---

## Public Chat API

### Protocol conventions

| Property | Contract |
|---|---|
| Base path | `https://<platform-domain>/api/v1` |
| Transport | HTTPS only |
| Request encoding | UTF-8 JSON |
| Streaming | `text/event-stream` |
| Non-streaming | `application/json` |
| Time format | ISO 8601 UTC |
| Identifiers | Opaque strings; clients must not infer structure |
| API versioning | Breaking changes require a new major path |
| Request tracing | Every response includes `X-Request-ID` |
| OpenAPI | Published from the gateway for the current API version |

### Credential types

| Credential | Example prefix | Location | Purpose |
|---|---|---|---|
| Dashboard access token | JWT | Browser dashboard | Management API authentication |
| Secret integration key | `ccn_sk_live_` | Customer server only | Create client tokens and call server-side Chat API |
| Client token | `ccn_ct_` | Browser or mobile client | Short-lived, chatbot-scoped chat access |
| Widget public key | `ccn_wpk_live_` | Website source | Widget bootstrap for configured origins |

Secret integration keys must never be embedded in browser JavaScript, mobile application bundles, public repositories, logs, or analytics events.

### Integration-key scopes

Supported scopes are:

- `chat:session:create`
- `chat:message:create`
- `chat:history:read`
- `chat:session:delete`
- `client_token:create`

A secret integration key is tenant-bound and may also be restricted to one chatbot.

### Create a client token

A customer backend creates a short-lived client token before allowing a browser or mobile client to call the Chat API directly.

```http
POST /api/v1/client-tokens HTTP/1.1
Authorization: Bearer ccn_sk_live_REDACTED
Content-Type: application/json
```

```json
{
  "chatbot_id": "bot_01J...",
  "external_user_id": "customer-user-4821",
  "origin": "https://support.customer.example",
  "scopes": [
    "chat:session:create",
    "chat:message:create",
    "chat:history:read"
  ],
  "ttl_seconds": 900,
  "metadata": {
    "customer_tier": "premium"
  }
}
```

```json
{
  "token": "ccn_ct_REDACTED",
  "token_type": "Bearer",
  "expires_at": "2026-07-11T09:15:00Z",
  "chatbot_id": "bot_01J..."
}
```

Rules:

- Maximum TTL is configured by the platform and cannot be exceeded by the request.
- The requested origin must be listed in the chatbot's allowed origins.
- The client token inherits a subset of the integration key's scopes.
- The client token cannot manage sources, credentials, tenants, or chatbot configuration.

### Create a chat session

```http
POST /api/v1/chat/sessions HTTP/1.1
Authorization: Bearer ccn_ct_REDACTED
Content-Type: application/json
Idempotency-Key: 0fbdd7c8-60b9-4c92-a62f-7882fccdbb71
```

```json
{
  "chatbot_id": "bot_01J...",
  "external_user_id": "customer-user-4821",
  "locale": "vi-VN",
  "metadata": {
    "channel": "custom-web-chat"
  }
}
```

```json
{
  "id": "ses_01J...",
  "chatbot_id": "bot_01J...",
  "external_user_id": "customer-user-4821",
  "locale": "vi-VN",
  "created_at": "2026-07-11T09:00:00Z",
  "status": "ACTIVE"
}
```

### Submit a message with streaming

Set `Accept: text/event-stream` to receive SSE.

```http
POST /api/v1/chat/sessions/ses_01J.../messages HTTP/1.1
Authorization: Bearer ccn_ct_REDACTED
Accept: text/event-stream
Content-Type: application/json
Idempotency-Key: 6adfa112-e88e-4e6d-b9d5-e61344527c7c
```

```json
{
  "content": "Sản phẩm khuyến mãi có được đổi trả không?",
  "metadata": {
    "screen": "order-details"
  }
}
```

Example stream:

```text
id: evt_01J...001
event: response.started
data: {"response_id":"rsp_01J...","message_id":"msg_01J...","session_id":"ses_01J..."}

id: evt_01J...002
event: response.delta
data: {"response_id":"rsp_01J...","sequence":1,"delta":"Theo chính sách "}

id: evt_01J...003
event: response.delta
data: {"response_id":"rsp_01J...","sequence":2,"delta":"đổi trả hiện hành, "}

id: evt_01J...004
event: citation
data: {"citation_id":"cit_01J...","source_id":"src_01J...","file_name":"return-policy.pdf","page":4,"unit_id":"unit_01J...","snippet":"Sản phẩm khuyến mãi..."}

id: evt_01J...005
event: response.completed
data: {"response_id":"rsp_01J...","finish_reason":"stop","usage":{"input_tokens":842,"output_tokens":97,"retrieved_units":6}}
```


### SSE event contract

| Event | Meaning | Required fields |
|---|---|---|
| `response.started` | Generation accepted | `response_id`, `message_id`, `session_id` |
| `response.delta` | Incremental text | `response_id`, `sequence`, `delta` |
| `citation` | Grounding source | `citation_id`, `source_id`, provenance fields |
| `response.completed` | Successful terminal event | `response_id`, `finish_reason`, `usage` |
| `response.failed` | Failed terminal event | `response_id`, `error` |

SSE behavior:

- The server sends `Cache-Control: no-cache`.
- Gateway buffering is disabled for SSE routes.
- A comment heartbeat is sent at a configured interval while generation is active.
- Event IDs are monotonically ordered within one response.
- A stream ends after `response.completed` or `response.failed`.
- A client must ignore unknown future event types.
- Partial text received before `response.failed` must be treated as incomplete.

### `curl` streaming example

```bash
curl -N \
  -X POST "https://<platform-domain>/api/v1/chat/sessions/ses_01J.../messages" \
  -H "Authorization: Bearer ${CACANODE_CLIENT_TOKEN}" \
  -H "Accept: text/event-stream" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  --data '{
    "content": "Chính sách hoàn tiền là gì?",
    "metadata": {"channel": "custom-ui"}
  }'
```

### Submit a message without streaming

Set `Accept: application/json`.

```http
POST /api/v1/chat/sessions/ses_01J.../messages HTTP/1.1
Authorization: Bearer ccn_sk_live_REDACTED
Accept: application/json
Content-Type: application/json
```

```json
{
  "content": "Chính sách hoàn tiền là gì?"
}
```

```json
{
  "id": "msg_01J...",
  "session_id": "ses_01J...",
  "role": "assistant",
  "content": "Khách hàng có thể yêu cầu hoàn tiền trong vòng 30 ngày...",
  "citations": [
    {
      "citation_id": "cit_01J...",
      "source_id": "src_01J...",
      "file_name": "refund-policy.pdf",
      "page": 3,
      "unit_id": "unit_01J...",
      "snippet": "Yêu cầu hoàn tiền được chấp nhận trong vòng 30 ngày..."
    }
  ],
  "usage": {
    "input_tokens": 812,
    "output_tokens": 88,
    "retrieved_units": 5
  },
  "created_at": "2026-07-11T09:01:12Z"
}
```

### Read session history

```http
GET /api/v1/chat/sessions/ses_01J.../messages?limit=50&after=msg_01J... HTTP/1.1
Authorization: Bearer ccn_ct_REDACTED
```

History access requires `chat:history:read`. A client token can read only sessions permitted by its chatbot and external-user scope.

### Delete a session

```http
DELETE /api/v1/chat/sessions/ses_01J... HTTP/1.1
Authorization: Bearer ccn_sk_live_REDACTED
```

Deletion is idempotent. Retention and audit metadata follow the configured legal and operational retention policy.

### Error envelope

```json
{
  "error": {
    "code": "KNOWLEDGE_BASE_NOT_READY",
    "message": "The chatbot knowledge base is still processing.",
    "request_id": "req_01J...",
    "details": {}
  }
}
```

| HTTP status | Code | Meaning |
|---:|---|---|
| `400` | `INVALID_REQUEST` | Invalid JSON, field, or request combination |
| `401` | `UNAUTHENTICATED` | Missing, invalid, expired, or revoked credential |
| `403` | `FORBIDDEN` | Scope, tenant, chatbot, origin, or subscription denial |
| `404` | `NOT_FOUND` | Resource does not exist in the authenticated scope |
| `409` | `KNOWLEDGE_BASE_NOT_READY` | Chatbot cannot answer because indexing is incomplete |
| `410` | `SESSION_EXPIRED` | Session is no longer active |
| `429` | `RATE_LIMITED` | Request or usage quota exceeded |
| `500` | `INTERNAL_ERROR` | Unexpected internal failure |
| `503` | `MODEL_UNAVAILABLE` | Generation service unavailable |
| `503` | `RETRIEVAL_UNAVAILABLE` | Vector or graph retrieval unavailable |

Raw stack traces, prompts, storage paths, internal hostnames, and model-server errors are never returned to public clients.

### Rate-limit headers

Public responses may include:

```text
RateLimit-Limit: 120
RateLimit-Remaining: 94
RateLimit-Reset: 41
```

Rate limits may be applied by tenant, integration key, client token, chatbot, IP address, and concurrent stream count.

### Idempotency

`POST` requests that create sessions, messages, client tokens, sources, or credentials accept `Idempotency-Key`.

- The key is scoped to the authenticated tenant and endpoint.
- Reusing the same key with the same request returns the original result.
- Reusing the same key with a different request returns `409 IDEMPOTENCY_CONFLICT`.
- Idempotency records expire after the configured retention period.

---

## Chat Widget

### Basic embed

```html
<script
  src="https://<widget-cdn-domain>/v1/cacanode-chat.js"
  data-chatbot-id="bot_01J..."
  data-widget-key="ccn_wpk_live_REDACTED"
  data-position="bottom-right"
  data-locale="vi-VN"
  defer>
</script>
```

The widget public key is safe to expose. It grants no management permission and is valid only for its chatbot and configured origins.

### Programmatic embed

```html
<div id="support-chat"></div>

<script src="https://<widget-cdn-domain>/v1/cacanode-chat.js" defer></script>
<script>
  window.addEventListener("cacanode:ready", () => {
    window.CacanodeChat.mount({
      target: "#support-chat",
      chatbotId: "bot_01J...",
      widgetKey: "ccn_wpk_live_REDACTED",
      mode: "inline",
      locale: "vi-VN",
      theme: {
        primaryColor: "#111827",
        borderRadius: 16
      },
      text: {
        title: "Hỗ trợ khách hàng",
        welcomeMessage: "Xin chào. Tôi có thể giúp gì cho bạn?",
        inputPlaceholder: "Nhập câu hỏi..."
      },
      behavior: {
        showCitations: true,
        persistSession: true,
        autoOpen: false
      }
    });
  });
</script>
```

### Widget configuration

| Group | Fields |
|---|---|
| Identity | Display name, logo URL, welcome message |
| Theme | Primary color, text color, background, border radius, launcher icon |
| Placement | Bottom right, bottom left, or inline target |
| Size | Width, height, mobile breakpoints |
| Language | Locale, input placeholder, fallback text |
| Behavior | Auto-open, session persistence, citation visibility, sound notification |
| Privacy | Consent text, history behavior, external-user metadata policy |
| Security | Allowed origins, widget enabled state |

Arbitrary script injection and arbitrary remote CSS injection are not supported. Customers requiring complete interface control use the Chat API.

### Widget browser API

```javascript
window.CacanodeChat.open();
window.CacanodeChat.close();
window.CacanodeChat.toggle();
window.CacanodeChat.resetSession();
window.CacanodeChat.setExternalUser({
  id: "customer-user-4821",
  metadata: { customerTier: "premium" }
});
```

The widget emits browser events:

- `cacanode:ready`
- `cacanode:opened`
- `cacanode:closed`
- `cacanode:session-created`
- `cacanode:message-started`
- `cacanode:message-completed`
- `cacanode:error`

No event contains secret credentials or hidden model instructions.

### Widget security flow

1. The browser loads the hosted script.
2. The widget sends its public key, chatbot ID, and browser origin to the bootstrap endpoint.
3. The platform verifies the origin against the chatbot allowlist.
4. The platform returns a short-lived client token.
5. The widget creates or restores a permitted chat session.
6. The widget sends messages through the public Chat API.

---

## Management API

Dashboard access uses JWT authentication. Integration keys are not accepted for tenant-management endpoints.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register business and initial tenant administrator |
| `POST` | `/api/v1/auth/login` | Issue access and refresh tokens |
| `POST` | `/api/v1/auth/refresh` | Rotate refresh token |
| `POST` | `/api/v1/auth/logout` | Revoke active session |
| `GET` | `/api/v1/tenants/current` | Read current tenant |
| `PATCH` | `/api/v1/tenants/current` | Update tenant profile |
| `GET` | `/api/v1/chatbots` | List tenant chatbots |
| `POST` | `/api/v1/chatbots` | Create chatbot |
| `GET` | `/api/v1/chatbots/{chatbotId}` | Read chatbot configuration |
| `PATCH` | `/api/v1/chatbots/{chatbotId}` | Update chatbot configuration |
| `GET` | `/api/v1/knowledge-bases` | List knowledge bases |
| `POST` | `/api/v1/knowledge-bases` | Create knowledge base |
| `POST` | `/api/v1/knowledge-bases/{knowledgeBaseId}/sources` | Upload source |
| `GET` | `/api/v1/knowledge-bases/{knowledgeBaseId}/sources` | List sources and ingestion states |
| `DELETE` | `/api/v1/sources/{sourceId}` | Delete source and derived data |
| `POST` | `/api/v1/sources/{sourceId}/reindex` | Reindex with active model versions |
| `GET` | `/api/v1/integration-keys` | List key metadata |
| `POST` | `/api/v1/integration-keys` | Create secret integration key |
| `POST` | `/api/v1/integration-keys/{keyId}/rotate` | Rotate key |
| `DELETE` | `/api/v1/integration-keys/{keyId}` | Revoke key |
| `GET` | `/api/v1/usage` | Read tenant usage and quota state |
| `GET` | `/api/v1/audit-events` | Read authorized audit events |
| `GET` | `/api/v1/public/billing/plans` | Read the public versioned plan catalog |
| `GET` | `/api/v1/billing/account` | Read the current subscription, quota windows, usage, features, and pending payment |
| `POST` | `/api/v1/billing/checkouts` | Create a server-priced PayOS Pro checkout; tenant administrator only |
| `GET` | `/api/v1/billing/payments/{paymentId}` | Poll an internal payment status after returning from PayOS |
| `POST` | `/api/v1/billing/downgrade` | Schedule paid Pro fallback or immediately end a trial |
| `POST` | `/api/v1/public/billing/payos/webhook` | Receive and verify PayOS payment notifications |

A newly created secret integration key is returned once. Only its prefix, fingerprint, scopes, timestamps, and one-way verification value are stored afterward.

---

## Billing, Subscriptions, and Quotas

The billing module is the source of truth for plan definitions, prices, subscription state, PayOS payment orders, verified webhook processing, reconciliation, reminders, and billing APIs. The tenant module owns the runtime entitlement projection used by Java and Python request paths.

### Plan catalog

The catalog is versioned backend configuration. Clients request catalog and account data from the API and never submit a payment amount.

| Plan | Price | Messages | Documents | Team members | Storage | Main features |
|---|---:|---:|---:|---:|---:|---|
| Starter | Free | 500 per month | 3 | 1 | 512 MB | Widget, dashboard summary, CacaNode branding |
| Pro trial | Free for 14 days | 10,000 for the trial | 50 | 5 | 10 GB | Pro technical entitlements |
| Pro monthly | 1,199,000 VND | 10,000 per month | 50 | 5 | 10 GB | API access, webhooks, advanced analytics, custom branding |
| Pro annual | 11,990,000 VND | 10,000 per month | 50 | 5 | 10 GB | Same entitlements as monthly Pro |
| Enterprise | Contact sales | Custom or unlimited | Custom or unlimited | Custom or unlimited | Custom | Sales-provisioned limits and features |

Enterprise numeric limits are nullable. A `null` limit means custom or unlimited and bypasses numeric quota checks.

### Subscription lifecycle

- Registration creates a 14-day Pro trial in the same transaction as the tenant account.
- Trial expiration moves directly to Starter without a grace period.
- A paid monthly term uses one calendar month from activation; an annual term uses one year.
- Annual subscriptions retain monthly message windows anchored to the original paid activation date.
- PayOS does not provide recurring subscription mandates, so renewals use new hosted payment links.
- Early renewal extends from the current `paidThroughAt` value and does not reset the active quota window early.
- Paid expiration enters a three-day Pro grace period. Grace retains the final quota window and does not grant another allowance.
- After grace, the tenant falls back to Starter. Existing documents, users, webhook configuration, and branding preferences remain stored.
- Choosing Starter during paid Pro schedules fallback after prepaid access and grace. Choosing Starter during trial ends the trial immediately.
- Version 1 does not provide refunds, prorating, automatic renewal, or automatic resource deletion.

### PayOS payment flow

Only `TENANT_ADMIN` users may create checkouts. The server resolves the amount and entitlement snapshot from the catalog, allocates the PayOS order code from a database sequence, and creates a payment link that expires after 30 minutes. `Idempotency-Key` is supported for checkout creation.

The browser return and cancel URLs control presentation only. The frontend polls CacaNode's payment-status endpoint every two seconds for up to thirty seconds and never treats PayOS query parameters as proof of payment.

Subscription activation requires a webhook verified through the pinned `vn.payos:payos-java:2.0.1` SDK. Processing checks the order code, payment-link ID, VND currency, and expected amount. Mismatches move the order to `REVIEW` and never activate entitlements. Successful duplicate webhooks are idempotent and do not extend the subscription twice.

Pending payments are reconciled against PayOS every five minutes. Rate-limit and server failures receive bounded retries and emit PayOS billing metrics.

### Quota and feature enforcement

- Message usage is stored in billing-anniversary `usage_metrics` periods. The Python chat service locks the tenant and atomically increments the applicable period row.
- Reaching the message limit returns the existing `MESSAGE_QUOTA_EXCEEDED` response with HTTP `429`.
- Document uploads lock the tenant entitlement row and reject before object storage when document count or storage would exceed the limit.
- Team-member limits count active members plus unexpired pending invitations and apply to invitations, acceptance, and reactivation.
- Downgrades preserve existing resources but block additional messages, uploads, invitations, and reactivations while usage exceeds Starter limits.
- Starter widget tokens remain usable. Creating or using `api:chat` tokens requires the API-access entitlement.
- Webhook endpoint configuration is preserved after downgrade, while create, test, secret rotation, and delivery are disabled.
- Starter retains dashboard summary analytics; detailed analytics require Trial, Pro, or Enterprise.
- Saved branding preference is preserved, but CacaNode branding is forced whenever custom branding is disabled.

Quota-warning notifications are emitted once per period at 80%, and quota-exceeded notifications are emitted at 100%. Paid renewal notices are created seven, three, and one day before expiration and daily during grace unless a Starter downgrade is already scheduled.

### Persistence and module boundaries

- `billing_subscriptions` stores one subscription per tenant, lifecycle timestamps, reminder state, catalog version, optimistic version, and a complete entitlement snapshot.
- `billing_payment_orders` stores internal payment IDs, tenant and user IDs, sequence-generated PayOS order codes, server-resolved prices, provider link data, status, and purchase snapshots.
- `billing_webhook_events` stores payload hashes and processing results without retaining unnecessary counterparty banking details.
- `usage_metrics` retains the legacy year and month fields while adding authoritative `period_start` and `period_end` timestamps.
- Billing applies runtime changes through `TenantModuleApi`; it does not access the tenant repository or pass JPA tenant entities across the module boundary.
- PayOS SDK types remain inside the payment-gateway adapter.

PayOS integration is disabled by default. PayOS has no separate sandbox, so final end-to-end verification requires an internal low-value live transaction after the production webhook URL is confirmed.

---

## Data and Storage Contracts

### Core entities

| Entity | Purpose |
|---|---|
| `Tenant` | Isolation and commercial boundary |
| `User` | Dashboard identity |
| `RoleAssignment` | Tenant-scoped authorization |
| `Subscription` | Entitlements and quotas |
| `Chatbot` | Behavior, knowledge base, origins, widget configuration |
| `KnowledgeBase` | Logical collection of tenant knowledge |
| `Source` | Uploaded document or media object |
| `KnowledgeUnit` | Normalized text, table, image, audio, or video segment |
| `ChatSession` | Multi-turn conversation boundary |
| `ChatMessage` | User or assistant message |
| `Citation` | Link from generated content to source provenance |
| `IntegrationKey` | Server-side API credential metadata |
| `ModelVersion` | Model, adapter, tokenizer, and runtime metadata |
| `IngestionJob` | Asynchronous processing state |
| `UsageRecord` | Metering event |
| `AuditEvent` | Security and administrative event |

### Normalized knowledge unit

```python
from dataclasses import dataclass, field
from typing import Any, Literal

Modality = Literal[
    "text",
    "table",
    "image",
    "audio",
    "video_segment",
    "ocr_text",
    "transcript",
]


@dataclass(frozen=True)
class KnowledgeUnit:
    tenant_id: str
    knowledge_base_id: str
    source_id: str
    unit_id: str
    modality: Modality

    text: str | None = None
    storage_uri: str | None = None

    page_number: int | None = None
    section_path: str | None = None
    sheet_name: str | None = None
    cell_range: str | None = None

    start_ms: int | None = None
    end_ms: int | None = None

    content_hash: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)
```

### PostgreSQL

PostgreSQL stores:

- Tenants, users, roles, and sessions.
- Chatbots and knowledge bases.
- Source metadata and ingestion state.
- Chat sessions, messages, and citations.
- Subscription entitlements and usage.
- Integration-key metadata.
- Model and adapter registry metadata.
- Audit records.

### SeaweedFS

Object keys follow tenant-scoped prefixes:

```text
tenants/{tenant_id}/sources/{source_id}/original/{filename}
tenants/{tenant_id}/sources/{source_id}/derived/pages/{page}.png
tenants/{tenant_id}/sources/{source_id}/derived/keyframes/{timestamp}.jpg
tenants/{tenant_id}/sources/{source_id}/derived/audio/{segment}.wav
tenants/{tenant_id}/sources/{source_id}/derived/transcripts/{version}.json
tenants/{tenant_id}/sources/{source_id}/derived/ocr/{version}.json
```

Objects are accessed through authorized backend services or short-lived signed URLs. Predictable public tenant paths are prohibited.

### Qdrant

The knowledge collection uses named vectors so incompatible embedding spaces are never mixed.

```json
{
  "collection": "knowledge_units_v2",
  "vectors": {
    "text_embeddinggemma_v1": {
      "size": 768,
      "distance": "Cosine"
    }
  },
  "sparse_vectors": {
    "text_bm25_v1": {"modifier": "idf"}
  }
}
```

Each point payload contains at least:

```json
{
  "tenant_id": "tenant_01J...",
  "knowledge_base_id": "kb_01J...",
  "source_id": "src_01J...",
  "unit_id": "unit_01J...",
  "modality": "video_segment",
  "language": "vi",
  "page_number": null,
  "sheet_name": null,
  "cell_range": null,
  "start_ms": 80000,
  "end_ms": 94000,
  "embedding_model": "embedding-model-id",
  "embedding_version": "1.0.0",
  "preprocessing_version": "1.0.0",
  "content_hash": "sha256-value"
}
```

Payload indexes exist for `tenant_id`, `knowledge_base_id`, `source_id`, `modality`, and other frequently filtered fields.

### Kuzu

Every graph node and relationship is tenant-scoped and evidence-backed.

```text
(:Source)-[:CONTAINS]->(:KnowledgeUnit)
(:KnowledgeUnit)-[:MENTIONS]->(:Entity)
(:Entity)-[:RELATED_TO {type, evidence_unit_id}]->(:Entity)
(:Policy)-[:APPLIES_TO]->(:Product)
```

A graph relationship without evidence provenance is not eligible for grounded answer generation.

### Model and embedding versioning

Every derived artifact records:

- Model family and checkpoint.
- Model revision.
- Adapter ID and version.
- Tokenizer version.
- Embedding dimension.
- Prompt or task prefix version.
- Parser, OCR, ASR, and chunker versions.
- Source content hash.
- Creation timestamp.

Changing an embedding checkpoint, dimension, tokenizer, normalization rule, or task prefix creates a new vector version. Migration uses a new named vector or collection, dual writes, background reindexing, validation, and controlled cutover.

### Reindex and v2 cutover

Build the new collection while chat continues reading v1:

```bash
cd rag-chatbot-fastapi
python -m app.maintenance.reindex_documents \
  --target-collection knowledge_units_v2 \
  --batch-size 50
```

The command is idempotent and supports `--dry-run`, `--tenant-id`, `--knowledge-base-id`,
`--after-id`, and `--updated-since`. It reads completed document metadata from PostgreSQL,
downloads originals from SeaweedFS, and reruns parsing, structural chunking, dense and sparse
encoding, Qdrant indexing, and graph extraction.

For cutover, pause uploads briefly, run a final `--updated-since` delta, compare completed-document
chunk counts and retrieval evaluation, then set `QDRANT_COLLECTION=knowledge_units_v2` and restart
chat/worker processes. Keep v1 until acceptance checks pass; rollback only requires switching the
environment variable back to v1.

---

## Vietnamese Model Adaptation

Fine-tuning changes model behavior. Retrieval supplies current tenant facts. Tenant documents are not automatically converted into training data.

### Gemma 4 adaptation

Gemma adapters are trained for:

- Natural Vietnamese customer-support language.
- Correct diacritics and punctuation.
- Text without diacritics and common misspellings.
- Regional expressions when supported by the approved dataset.
- Vietnamese-English technical code-switching.
- Grounded answering from supplied context.
- Explicit unavailable answers when context is insufficient.
- Structured JSON for query routing and graph extraction.
- Stable citation behavior.

Parameter-efficient fine-tuning uses LoRA or QLoRA. Base checkpoints remain immutable. Serving references an approved base-model version and adapter version.

### EmbeddingGemma adaptation

Embedding fine-tuning uses Vietnamese query, positive, and hard-negative examples.

```json
{
  "query": "Sản phẩm khuyến mãi có được đổi trả không?",
  "positive": "Sản phẩm mua trong chương trình khuyến mãi được đổi trong 7 ngày nếu còn nguyên tem.",
  "hard_negative": "Sản phẩm thông thường được hoàn tiền trong vòng 30 ngày."
}
```

Evaluation includes queries with:

- Correct Vietnamese diacritics.
- Missing diacritics.
- Common spelling errors.
- Abbreviations.
- Regional phrasing.
- English product and technical terms.
- Semantically close but factually incorrect negatives.
- No-answer cases.

### Whisper, OCR, CLIP, and CLAP

- Whisper is evaluated with Vietnamese word error rate on real audio conditions.
- OCR is evaluated with character error rate on Vietnamese scans, tables, subtitles, and low-resolution frames.
- CLIP is evaluated with Vietnamese image-text retrieval pairs.
- CLAP is evaluated with Vietnamese audio-text retrieval pairs.

### Training-data policy

- Training data must be owned, licensed, or explicitly authorized.
- Tenant data is excluded from shared training by default.
- Any tenant-data use requires explicit written authorization and documented scope.
- Sensitive information is removed or protected before training.
- Dataset versions are immutable and auditable.
- Training runs record data version, code version, checkpoint, hyperparameters, and metrics.

### Model promotion

A model or adapter is served only after passing:

- Vietnamese retrieval evaluation.
- Grounded-generation evaluation.
- Citation correctness checks.
- Safety and prompt-injection tests.
- Latency and memory checks.
- Regression tests against the active version.

---

## Security and Tenant Isolation

### Authentication and credential storage

- Dashboard access uses short-lived JWT access tokens and rotating refresh tokens.
- Secret integration keys are shown once and stored as one-way verification values with a server-side pepper.
- Client tokens are short-lived, signed, chatbot-scoped, and scope-restricted.
- Widget public keys contain no secret privilege.
- Credential rotation and revocation take effect without service restart.
- Secrets are injected through the deployment secret store and never committed.

### Tenant isolation

Tenant scope is enforced at every layer:

- PostgreSQL queries include tenant scope or database-enforced isolation.
- Qdrant queries include server-generated tenant and knowledge-base filters.
- Kuzu traversals begin from tenant-scoped nodes and reject cross-tenant edges.
- SeaweedFS objects use tenant prefixes and authorized access.
- Redis keys include tenant scope.
- RabbitMQ jobs contain signed or validated tenant and source context.
- Logs and metrics avoid raw tenant content.

A tenant ID from request JSON, query parameters, or widget configuration is never used as authorization without matching authenticated context.

### Browser and widget security

- CORS is allowlist-based.
- Widget bootstrap validates the actual request origin.
- Secret integration keys are rejected from browser-only flows where exposure is detected.
- Content Security Policy is supported for the widget host.
- The widget uses HTTPS and does not inject arbitrary customer scripts.
- External user IDs are treated as opaque values and do not grant authorization.

### Upload security

- File type, signature, size, and archive depth are validated.
- Malware scanning occurs before parsing.
- Parser processes run with restricted filesystem and network access.
- Decompression bombs and oversized media are rejected.
- Generated paths cannot escape tenant-scoped storage.

### AI security

- Uploaded documents are untrusted context.
- Document instructions cannot override platform or chatbot policy.
- Retrieved HTML is sanitized before rendering.
- Generated citations are validated against retrieved units.
- The model server is private and cannot access tenant storage directly unless required through a controlled service.
- No automatic fallback sends tenant data to an external AI provider.

### Audit

The platform records security-relevant events, including:

- Login, logout, refresh, and failed authentication.
- User invitations and role changes.
- Chatbot and origin changes.
- Source upload, deletion, and reindexing.
- Integration-key creation, rotation, and revocation.
- Subscription and quota changes.
- Platform-administrator access to tenant resources.

---

## Performance and Availability

### Service objectives

| Operation | Objective under normal load |
|---|---|
| Chat time to first streamed token | p95 at or below 3 seconds |
| Upload request acknowledgement | p95 at or below 2 seconds, excluding background ingestion |
| Management console initial load | p95 at or below 3 seconds on standard broadband |
| Concurrent chat sessions | At least 50 without material response-time degradation |
| SSE heartbeat | Sent before common proxy idle timeouts |

Model cold starts, very large context, GPU saturation, reindexing, and unavailable dependencies are reported separately from normal-load measurements.

### Scaling

- Business API and AI API are stateless and horizontally scalable.
- SSE connections may use connection-aware load balancing; session state remains external.
- Workers scale independently by queue and modality.
- Model servers scale by replica, tensor parallelism, or deployment shard.
- Qdrant payload indexes support tenant-filtered retrieval.
- RabbitMQ controls ingestion backpressure.

### Failure behavior

- Chat requests do not fall back to external model providers.
- Model unavailability returns `503 MODEL_UNAVAILABLE`.
- Retrieval unavailability returns `503 RETRIEVAL_UNAVAILABLE`.
- A failed ingestion stage records a retryable or terminal error code.
- Retries use bounded exponential backoff and dead-letter queues.
- Public errors include a request ID and safe message.

### Observability

The platform exports:

- Request rate, latency, status, and active SSE streams.
- Time to first token and tokens per second.
- Retrieval latency and result counts by modality.
- Qdrant and Kuzu latency.
- Queue depth, retry count, and dead-letter count.
- Ingestion duration by file type and stage.
- GPU memory, utilization, batch size, and model queue time.
- Tenant usage counters without raw message content.

Logs are structured JSON and include `request_id`, `tenant_id`, `chatbot_id`, and `session_id` where permitted.

---

## Configuration

Create `.env` from `.env.example`. Production values are provided by the deployment secret store.
Authentication uses the existing HS256 JWT setup: Spring signs access and verification tokens with `TOKEN_KEY`; refresh tokens remain opaque server-stored values, and `EXPIRY_DAYS` controls their cookie/storage lifetime.

```dotenv
# Runtime
APP_ENV=development
PUBLIC_API_BASE_URL=http://localhost/api/v1
PUBLIC_WIDGET_URL=http://localhost/widget/v1/cacanode-chat.js
ADMIN_WEB_URL=http://localhost:5173
DEFAULT_LOCALE=vi-VN

# Authentication
TOKEN_KEY=change-me
EXPIRY_MINS=15
EXPIRY_DAYS=30

# PostgreSQL
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=cacanode
POSTGRES_USER=cacanode
POSTGRES_PASSWORD=change-me

# Redis and RabbitMQ
REDIS_URL=redis://redis:6379/0
RABBITMQ_URL=amqp://guest:guest@rabbitmq:5672/

# SeaweedFS
SEAWEEDFS_MASTER_URL=http://seaweedfs-master:9333
SEAWEEDFS_FILER_URL=http://seaweedfs-filer:8888
SEAWEEDFS_S3_ENDPOINT=http://seaweedfs-s3:8333
SEAWEEDFS_BUCKET=cacanode
SEAWEEDFS_CONNECT_TIMEOUT_SECONDS=3
SEAWEEDFS_READ_TIMEOUT_SECONDS=30
SEAWEEDFS_MAX_ATTEMPTS=3

# Qdrant
QDRANT_URL=http://qdrant:6333
QDRANT_API_KEY=
QDRANT_COLLECTION=knowledge_units_v2
QDRANT_DENSE_VECTOR_NAME=text_embeddinggemma_v1
QDRANT_SPARSE_VECTOR_NAME=text_bm25_v1
QDRANT_TENANT_FIELD=tenant_id
QDRANT_KNOWLEDGE_BASE_FIELD=knowledge_base_id

# Kuzu
KUZU_DATABASE_PATH=/data/kuzu/cacanode.kuzu

# Hosted answer generation and graph extraction
LLM_PROVIDER=openai
OPENAI_API_KEY=<openai-api-key>
OPENAI_MODEL=o4-mini
LLM_MAX_OUTPUT_TOKENS=1024

# Local text embeddings through embedding-only Ollama
TEXT_EMBEDDING_BASE_URL=http://ollama:11434
TEXT_EMBEDDING_MODEL_ID=embeddinggemma
TEXT_EMBEDDING_DIMENSION=768
TEXT_EMBEDDING_BATCH_SIZE=16
TEXT_EMBEDDING_TIMEOUT_SECONDS=120
SPARSE_MODEL_ID=Qdrant/bm25
SPARSE_MODEL_CACHE_DIR=/models/fastembed

# Vision, audio, ASR, and OCR
CLIP_MODEL_ID=<approved-clip-checkpoint>
CLAP_MODEL_ID=<approved-clap-checkpoint>
WHISPER_MODEL_ID=<approved-whisper-checkpoint>
OCR_ENGINE=paddleocr
OCR_LANGUAGE=vi
FFMPEG_BINARY=ffmpeg

# Model registry
MODEL_REGISTRY_TOKEN=
MODEL_CACHE_DIR=/models
AI_DEVICE=cuda
AI_DTYPE=auto

# Upload limits
MAX_DOCUMENT_MB=20
MAX_IMAGE_MB=20
MAX_AUDIO_MB=200
MAX_VIDEO_MB=500
MALWARE_SCAN_ENABLED=true

# Retrieval
DENSE_CANDIDATE_COUNT=40
SPARSE_CANDIDATE_COUNT=40
GRAPH_CANDIDATE_COUNT=20
FUSION_CANDIDATE_COUNT=30
RRF_K=30
SEMANTIC_DENSE_WEIGHT=0.55
SEMANTIC_SPARSE_WEIGHT=0.30
SEMANTIC_GRAPH_WEIGHT=0.15
EXACT_DENSE_WEIGHT=0.25
EXACT_SPARSE_WEIGHT=0.60
EXACT_GRAPH_WEIGHT=0.15
RELATIONAL_DENSE_WEIGHT=0.30
RELATIONAL_SPARSE_WEIGHT=0.15
RELATIONAL_GRAPH_WEIGHT=0.55
CALCULATION_DENSE_WEIGHT=0.35
CALCULATION_SPARSE_WEIGHT=0.50
CALCULATION_GRAPH_WEIGHT=0.15
IMAGE_TOP_K=12
AUDIO_TOP_K=12
GRAPH_MAX_HOPS=3
PRIMARY_CONTEXT_TOP_K=5
FINAL_CONTEXT_TOP_K=8
CONTEXT_DOCUMENT_SOFT_LIMIT=2
NEIGHBOR_EXPANSION_LIMIT=3
RERANKER_ENABLED=true
RERANKER_URL=http://reranker-service
RERANKER_MODEL_ID=cross-encoder/mmarco-mMiniLMv2-L12-H384-v1
RERANKER_TIMEOUT_SECONDS=20
ENABLE_GENERAL_KNOWLEDGE=false

# Public API
SSE_HEARTBEAT_SECONDS=15
IDEMPOTENCY_TTL_HOURS=24
PUBLIC_RATE_LIMIT_PER_MINUTE=120
MAX_CONCURRENT_STREAMS_PER_TENANT=50

# Billing and PayOS
PAYOS_ENABLED=false
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_RETURN_URL=http://localhost:3000/settings?tab=quota&payment=return
PAYOS_CANCEL_URL=http://localhost:3000/settings?tab=quota&payment=cancel
BILLING_SALES_URL=mailto:sales@cacanode.com
BILLING_CATALOG_VERSION=2026-07-15

# Observability
LOG_LEVEL=INFO
OTEL_EXPORTER_OTLP_ENDPOINT=
DISABLE_EXTERNAL_TELEMETRY=true
```

No environment variable accepts a tenant-supplied model-provider key.

---

## Local Deployment

### Prerequisites

- Git.
- Docker Engine.
- Docker Compose.
- Sufficient disk space for model weights, raw sources, and derived media.

### Start local development

The development infrastructure target starts PostgreSQL, Redis, RabbitMQ, Qdrant, Ollama,
SeaweedFS, and the Kuzu graph service through Docker Compose. The AI/chat FastAPI app runs on the
host with auto-reload:

```bash
cd rag-chatbot-fastapi
cp .env.example .env
make dev
```

The AI API reaches Graph on `http://localhost:8010`. Reranking is disabled by default locally
because TEI does not publish an ARM64 image. Large BGE rerankers can exhaust Docker Desktop memory
under x86 emulation, starving Ollama and SeaweedFS. Local and low-resource production opt-in uses
the much smaller Vietnamese-capable `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1` model instead.
Start it explicitly with `make dev-reranker`, then run the API with
`DEV_RERANKER_ENABLED=true make dev`. The default `make dev` mode also starts worker lifecycles
inside the FastAPI process with `WORKER_MODE=embedded`.

Stop the development containers without deleting their volumes using:

```bash
make dev-down
```

### Start the container platform

The production profile is designed for a 4-vCPU / 8-GB CPU droplet. It uses hosted OpenAI
generation, embedding-only Ollama, the Kuzu graph service, and a lightweight CPU multilingual
reranker. Follow [DEPLOYMENT.md](DEPLOYMENT.md) for DNS, HTTPS, droplet bootstrap, GitHub Actions,
secrets, smoke tests, and rollback.

For a manual start after creating `.env.production`:

```bash
COMPOSE_PARALLEL_LIMIT=1 docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  up -d --build
```

The normal production mode runs one embedded document worker. If dedicated ingestion is required,
disable the embedded worker and start only the dedicated document profile:

```bash
WORKER_MODE=disabled docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  --profile dedicated-workers up -d document-worker
```

The production Compose deployment exposes these logical services:

```text
caddy
gateway
admin-web
business-api
ai-api
graph-service
document-worker
ollama
reranker-service
postgres
redis
rabbitmq
qdrant
seaweedfs-master
seaweedfs-volume
seaweedfs-filer
```

### Check status

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml ps
./deploy/smoke-test.sh https://app.example.com
```

### View logs

```bash
docker compose logs -f postgres redis rabbitmq qdrant graph-service
docker compose --env-file .env.production -f docker-compose.prod.yml \
  logs -f caddy gateway business-api ai-api reranker-service ollama
```

### Stop the platform

```bash
docker compose down
docker compose --env-file .env.production -f docker-compose.prod.yml down
```

Use `docker compose down -v` only when intentionally deleting local databases, queues, vectors, graph data, and object-storage volumes.

### Health endpoints

| Endpoint | Meaning |
|---|---|
| `/health/live` | Process is running |
| `/health/ready` | Required dependencies are available |
| `/metrics` | Private Prometheus-compatible metrics endpoint |

Readiness for the AI API requires the active model, embedding service, Qdrant, Kuzu, PostgreSQL, and Redis dependencies needed for the requested operation.

---

## Testing and Release Gates

### Required automated tests

- Authentication and refresh-token rotation.
- Role and scope enforcement.
- Tenant isolation across PostgreSQL, Qdrant, Kuzu, SeaweedFS, Redis, and queues.
- Integration-key creation, rotation, revocation, and one-time display.
- Client-token expiry, origin, chatbot, user, and scope restrictions.
- Chat session lifecycle.
- Streaming event order and terminal-event behavior.
- Non-streaming response schema.
- Idempotent session and message creation.
- Source upload, ingestion, reindex, and deletion.
- Citation provenance validation.
- Prompt-injection resistance.
- Cross-tenant retrieval prevention.
- Rate limits and concurrent-stream quotas.
- Server-side plan price and entitlement resolution.
- Trial expiration, paid grace, Starter fallback, early renewal, and annual monthly quota windows.
- Valid, invalid, duplicate, unknown, mismatched, and reconciled PayOS payment events.
- Checkout administrator authorization, idempotency, payment polling, and downgrade behavior.
- Exact-limit and concurrent message, document, storage, and team-member enforcement.
- API access, webhook, analytics, and custom-branding feature gates after downgrade.
- Model, vector, and parser version migrations.

### Retrieval evaluation

Text retrieval is evaluated with:

- Recall@5 and Recall@10.
- Mean Reciprocal Rank.
- nDCG.
- No-answer precision.
- Vietnamese diacritic and spelling variants.

Multimodal retrieval is evaluated with modality-specific Recall@K and timestamp accuracy.

The versioned Vietnamese fixture is `rag-chatbot-fastapi/tests/data/retrieval_vi_v1.json`. Score
recorded rankings for the dense-only, dense+sparse, dense+graph, and full-pipeline ablations with:

```bash
python -m app.maintenance.evaluate_retrieval \
  --dataset tests/data/retrieval_vi_v1.json \
  --results artifacts/full-pipeline.json \
  --label full-pipeline
```

The report includes Recall@5/10, MRR, nDCG@10, no-answer precision, channel contribution, and p95
latency without logging query or document text.

### Generation evaluation

Generation is evaluated for:

- Groundedness.
- Answer correctness.
- Citation correctness.
- Vietnamese fluency.
- Refusal to invent unavailable tenant facts.
- Stable structured output.
- Safety-policy compliance.

### Performance tests

Performance testing includes:

- p50, p95, and p99 time to first token.
- Stream duration and tokens per second.
- 50 or more concurrent sessions.
- Queue backpressure under mixed document, audio, and video ingestion.
- Model restart and dependency-failure behavior.
- Large-tenant filtered retrieval.

A release cannot replace the active model, embedding version, parser version, or public API version without passing its applicable regression suite.

---

## License

Cacanode source code is proprietary software. Access to the hosted service, Chat API, and hosted widget is governed by the applicable commercial agreement. No source-code rights are granted by service access.

```text
Copyright (c) 2026 LinkedNodeDigital. All rights reserved.
Unauthorized copying, modification, or distribution is prohibited.
```

Third-party libraries, services, and model weights remain subject to their own licenses and terms. Their inclusion does not change the proprietary status of Cacanode source code.

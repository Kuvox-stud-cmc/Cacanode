# Cacanode

Cacanode is a proprietary, Vietnamese-first, multi-tenant SaaS platform for building document-grounded conversational applications with Graph Retrieval-Augmented Generation (GraphRAG).

The product exposes two chat delivery surfaces:

1. **Cacanode Chat API** — a headless JSON API for customers that build their own chatbot UI, mobile chat, support console, or conversational workflow.
2. **Cacanode Chat Widget** — a hosted, configurable JavaScript widget for customers that need a ready-to-embed website chatbot.

Both surfaces use the same tenant-isolated knowledge base, chatbot configuration, retrieval pipeline, conversation model, usage limits, and source-citation format.

| Property | Value |
|---|---|
| Product type | Hosted multi-tenant SaaS |
| Primary market | Vietnamese businesses and Vietnamese-language applications |
| Integration modes | Headless Chat API and hosted JavaScript widget |
| Public protocol | JSON REST over HTTPS; Spring synchronously delegates inference over gRPC |
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
- Backend-authoritative Starter, Trial, Pro, Business, and Enterprise entitlements with PayOS-hosted checkout, verified webhook activation, manual renewal, reconciliation, quota enforcement, and subscription lifecycle management.
- Spring-owned chat/session contracts, idempotency, quota accounting, citations, and conversation persistence.
- A stateless internal FastAPI gRPC inference service with generation-result deduplication, structural ingestion, dense/sparse/graph retrieval, reranking, health checks, and worker lifecycles.
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
- [Academic AI and Information-Retrieval Reference](#academic-ai-and-information-retrieval-reference)
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

Commercial entitlements are represented by tenant subscription records and projected onto the tenant runtime. Self-service Pro and Business purchases use server-created PayOS payment links, and activation occurs after a verified PayOS webhook or an authoritative PayOS payment-status reconciliation. Enterprise provisioning remains sales-led.

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
- Responses are returned as completed JSON after the internal unary gRPC generation completes.
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

    Gateway --> Business[Spring Boot Public and Business API]

    Business --> PostgreSQL[(PostgreSQL)]
    Business --> Redis[(Redis)]
    Business --> RabbitMQ[(RabbitMQ)]
    Business -->|mTLS gRPC| AI[Stateless FastAPI Inference]

    AI --> Orchestration[LangChain Orchestration Adapters]
    AI --> Redis
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

    AI -. no PostgreSQL network .- PostgreSQL
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

### Core runtime sequences

The following sequences reflect the delivered Spring Boot and FastAPI control paths. Each workflow
is split at its natural boundary so the diagrams retain implementation detail without forcing too
many participant lanes into one viewport.

#### External chat turn

##### 1. Request control and persistence

```mermaid
sequenceDiagram
    autonumber
    actor Client as App or widget
    participant Gateway as Nginx gateway
    participant API as Spring chat API
    participant DB as PostgreSQL
    participant AI as FastAPI inference

    Client->>Gateway: POST a session message
    Gateway->>API: Forward token and origin<br/>request ID and idempotency key
    API->>API: Authenticate scope<br/>and widget parent origin
    API->>DB: Lock session and check idempotency

    alt Completed idempotent replay
        DB-->>API: Previously stored assistant response
        API-->>Gateway: Replay identical JSON response
        Gateway-->>Client: HTTP 200
    else New or retryable turn
        API->>DB: Consume quota and persist or reuse<br/>the user message; set turn PENDING
        API->>AI: Unary GenerateAnswer<br/>with scope, history, and revision
        AI-->>API: Answer, citations, action, and usage
        API->>DB: Lock session and compare the knowledge revision
        alt Revision is current
            API->>API: Validate citation visibility
            API->>DB: Persist assistant message<br/>and mark turn COMPLETED
        else Revision changed during generation
            API->>DB: Rebuild context at the latest revision
            API->>AI: Retry once with the same turn
            AI-->>API: Regenerated answer
            API->>DB: Revalidate and finalize or mark the turn failed
        end
        API-->>Gateway: Completed JSON response or explicit error
        Gateway-->>Client: HTTP response
    end

    Note over API,DB: Failed turns are marked FAILED<br/>and their quota increment is rolled back.
```

##### 2. Inference and retrieval

```mermaid
sequenceDiagram
    autonumber
    participant API as Spring chat API
    participant AI as FastAPI inference
    participant Cache as Redis result cache
    participant Models as Embedding + LLM
    participant Retrieval as Qdrant + Kuzu<br/>+ optional reranker

    API->>AI: GenerateAnswer with authoritative scope
    AI->>Cache: Look up generation ID

    alt Generation result already exists
        Cache-->>AI: Cached protobuf response
    else Fresh inference
        AI->>Models: Embed the question
        Models-->>AI: Query vector
        AI->>Retrieval: Search by tenant, knowledge base,<br/>revision, and document visibility
        Retrieval->>Retrieval: Dense + sparse + graph fusion<br/>then optional reranking
        Retrieval-->>AI: Ranked evidence with provenance
        AI->>Models: Generate grounded completion
        Models-->>AI: Answer and token usage
        AI->>Cache: Store result for generation-ID deduplication
    end

    AI-->>API: Answer, citations, optional action, and usage
    Note over AI,Cache: Redis failures are fail-open;<br/>generation can continue without the cache.
```

#### Durable document ingestion

##### 1. Upload acceptance and durable publication

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Tenant user
    participant Gateway as Nginx gateway
    participant API as Spring control plane
    participant DB as PostgreSQL<br/>document + outbox
    participant Object as SeaweedFS
    participant MQ as RabbitMQ

    Admin->>Gateway: POST multipart document
    Gateway->>API: Forward authenticated upload
    API->>API: Validate role, knowledge base,<br/>file type, signature, size, and quota
    API->>DB: Create PENDING document and job ID
    API->>Object: Store the raw source
    Object-->>API: Storage key confirmed
    API->>DB: Add document.ingest.requested<br/>outbox row and commit
    API-->>Gateway: 202 Accepted with document and job IDs
    Gateway-->>Admin: PENDING status

    Note over Admin,DB: The synchronous upload request ends at HTTP 202.

    loop Due outbox rows
        API->>DB: Lock unpublished event
        API->>MQ: Publish persistent ingestion request
        MQ-->>API: Publisher confirmation
        API->>DB: Mark event PUBLISHED
    end

    Note over API,MQ: Failed publications remain in the outbox<br/>and retry with capped exponential backoff.
```

##### 2. Worker indexing and status propagation

```mermaid
sequenceDiagram
    autonumber
    participant MQ as RabbitMQ
    participant Worker as Document worker
    participant Checkpoint as Redis checkpoints
    participant Object as SeaweedFS
    participant Models as Embedding + extraction
    participant Index as Qdrant + Kuzu
    participant Control as Spring listener<br/>+ PostgreSQL

    MQ->>Worker: Deliver ingestion request
    Worker->>Checkpoint: Claim job lease and checkpoint phase
    Worker->>MQ: Publish PROCESSING status
    MQ->>Control: Deliver status event
    Control->>Control: Deduplicate inbox event<br/>and set PROCESSING
    Worker->>Object: Download raw source
    Object-->>Worker: Source bytes
    Worker->>Worker: Parse, normalize, and structure-aware chunk
    Worker->>Models: Create dense and sparse representations
    Models-->>Worker: Embeddings and sparse vectors
    Worker->>Index: Replace Qdrant document index
    Worker->>Models: Extract grounded entities and relations<br/>or build structural graph units
    Models-->>Worker: Graph facts and evidence links
    Worker->>Index: Replace Kuzu source graph

    alt Pipeline completed
        Worker->>MQ: Publish COMPLETED with chunk count
        MQ->>Control: Deliver status event
        Control->>Control: Set COMPLETED<br/>and increment search revision
        Worker->>Checkpoint: Mark COMPLETE and release lease
    else Permanent or exhausted failure
        Worker->>Index: Delete partial index data when required
        Worker->>MQ: Publish FAILED with safe error
        MQ->>Control: Deliver status event
        Control->>Control: Set FAILED
        Worker->>Checkpoint: Mark FAILED and release lease
    end

    Note over MQ,Worker: Transient failures are retried;<br/>terminal requests use dead-letter routing.
```

#### PayOS checkout and activation

##### 1. Checkout creation

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Tenant admin
    participant Console as Management console
    participant Gateway as Nginx gateway
    participant API as Spring billing module
    participant DB as PostgreSQL
    participant PayOS as PayOS

    Admin->>Console: Select Pro or Business billing interval
    Console->>Gateway: POST /api/v1/billing/checkouts
    Gateway->>API: Forward JWT and idempotency key
    API->>API: Require TENANT_ADMIN and resolve catalog price
    API->>DB: Lock account and check idempotency
    API->>DB: Persist PENDING payment<br/>and entitlement snapshot
    API->>PayOS: Create expiring hosted payment link
    PayOS-->>API: Payment-link ID and checkout URL
    API->>DB: Attach provider identity and checkout URL
    API-->>Gateway: 201 Created with checkout URL
    Gateway-->>Console: Checkout details
    Console->>PayOS: Open hosted checkout
    Admin->>PayOS: Complete payment
    PayOS-->>Console: Return to payment-status page

    Note over Console,API: Return and cancel URLs control presentation only;<br/>they never prove that payment succeeded.
```

##### 2. Verified activation and reconciliation

```mermaid
sequenceDiagram
    autonumber
    participant Console as Management console
    participant Gateway as Nginx gateway
    participant API as Spring billing module
    participant DB as PostgreSQL
    participant PayOS as PayOS

    alt Signed webhook trigger
        PayOS->>Gateway: POST public webhook
        Gateway->>API: Forward payload
        API->>API: Deduplicate payload and verify SDK signature
        API->>DB: Lock payment order and subscription
    else Browser polling trigger
        Console->>Gateway: GET /api/v1/billing/payments/{paymentId}
        Gateway->>API: Poll internal payment status
        API->>DB: Lock payment order and subscription
        API->>PayOS: Read authoritative provider status
        PayOS-->>API: Current payment state
    else Five-minute reconciliation trigger
        API->>DB: Find open, unexpired payment orders
        API->>DB: Lock each payment order and subscription
        API->>PayOS: Read authoritative provider status
        PayOS-->>API: Current payment state
    end

    API->>API: Validate order code, payment-link ID,<br/>currency or paid amount, and expected amount

    alt Provider identity or amount mismatch
        API->>DB: Mark payment REVIEW without activation
    else Provider data matches
        alt Provider confirms PAID
            API->>DB: Mark PAID and activate subscription
            API->>DB: Project entitlements<br/>and cancel other open orders
        else Payment is not paid
            API->>DB: Update provider status without activation
        end
    end

    opt Browser requested the payment status
        API-->>Gateway: Internal payment status
        Gateway-->>Console: Render authoritative status
    end
    Note over API,DB: Every trigger shares the same idempotent activation logic;<br/>duplicate success never extends the subscription twice.
```

---

## Service Responsibilities

| Service | Responsibilities |
|---|---|
| API Gateway | TLS termination, routing, request IDs, CORS, response buffering controls, public rate limiting |
| Management Console | Tenant, chatbot, source, credential, widget, and usage administration |
| Hosted Widget | Browser chat UI, client-token bootstrap, JSON rendering, local session state |
| Spring Boot Business API | All public REST routes; identity, integration-token authentication, sessions/messages/turns, idempotency, quota, citations, revisions, webhooks, documents, and PostgreSQL ownership |
| FastAPI inference service | Internal unary gRPC generation, Qdrant document-unit reads, derived-index deletion, retrieval, context assembly, GraphRAG execution, and no PostgreSQL access |
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
| Redis | Rate-limit counters, Spring business caches, AI embedding/retrieval/semantic caches, and ten-minute generation-ID result deduplication |
| RabbitMQ | Durable asynchronous ingestion and reindex jobs through Spring outbox/inbox, publisher confirms, retry, and DLQ routing |

### Redis cache status

Spring and FastAPI provide shared, fail-open cache infrastructure. Spring uses a string-key,
raw-byte `RedisTemplate` for business caches while retaining `StringRedisTemplate` for public rate
limiting. FastAPI reuses one application-lifetime binary Redis client for embeddings, retrieval,
semantic answers, and generation-ID deduplication. Optional caches are
implemented but disabled by default.

PostgreSQL remains authoritative for business, identity, billing, document, and chat data. Qdrant and Kuzu remain authoritative for retrieval and graph data. Redis errors never replace a successful authoritative read with an HTTP failure.

Available cache groups cover Spring business reads, model-versioned
embeddings, and knowledge-revision-aware retrieval results. Enable and measure one domain at a
time. Generated LLM answers are not cached, and no distributed cache lock is active. Keys use the
versioned `ccn:v1` namespace; tenant-owned values use trusted tenant scope, and free-form text is
hashed rather than stored in keys. See the [caching guide](docs/CACHING.md) for TTLs, invalidation,
metrics, tests, evidence requirements, and rollback.

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
PENDING -> PROCESSING -> COMPLETED
PENDING -> FAILED
PROCESSING -> FAILED
```

These are the persisted public document statuses. Redis worker checkpoints separately track
fine-grained phases such as the processing-status publication, vector-index replacement,
graph replacement, completion publication, cleanup, and terminal failure.

Each public status record contains:

- document and ingestion job IDs
- file name, type, size, visibility, and knowledge-base ID
- current status
- chunk count when processing succeeds
- safe error message when processing fails
- upload timestamp

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
- Source-specific cache entries when a later cache phase enables them.
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
    Route --> GraphQuery[Scoped graph query]

    DenseQuery --> DenseSearch[Qdrant dense top 40]
    SparseQuery --> SparseSearch[Qdrant sparse top 40]
    GraphQuery --> GraphSeed[Kuzu entity and alias seed matching]
    GraphSeed --> GraphTraverse[Bounded bidirectional RELATED_TO traversal]
    GraphTraverse --> GraphEvidence[Grounded path-evidence ranking top 20]

    DenseSearch --> Fusion[Adaptive weighted RRF, k=30]
    SparseSearch --> Fusion
    GraphEvidence --> Fusion

    Fusion --> Rerank[Optional TEI bge-reranker-v2-m3]
    Rerank --> Primary[Select five diverse primary units]
    Primary --> Context[Add up to three eligible neighbors]
    Context --> Generate[Grounded LLM generation]
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
- Graph traversal is limited to zero through three hops, rejects cycles, and filters document scope
  before the graph candidate limit.
- The current implementation returns an unavailable answer when no context unit is available. Calibrated
  score-based abstention remains an evaluation and research task.
- Uploaded content is untrusted data and cannot override system or chatbot policy.

### Grounding rules

- Tenant-specific factual claims must be supported by retrieved context.
- Citations are returned as structured objects, not only rendered text.
- General knowledge is disabled by default and may be enabled per chatbot.
- The model must distinguish unavailable information from system failure.
- Internal prompts, hidden instructions, and raw model traces are never returned through the public API.

---

## Academic AI and Information-Retrieval Reference

This section is the canonical technical reference for a university report, thesis chapter, or
experimental paper based on Cacanode. It distinguishes project-owned algorithms from pretrained-model
inference and from infrastructure integration so that academic claims remain reproducible and accurate.

The strongest implemented research direction is an **adaptive hybrid Retrieval-Augmented Generation
(RAG) pipeline with evidence-grounded multi-hop graph search and safety-constrained semantic answer
caching**. The repository does not currently contain neural-network training, back-propagation,
LoRA/QLoRA, optimizer, loss-function, or checkpoint creation code. It does contain substantial
project-owned Information Retrieval (IR), graph search, grounding, semantic-cache, structural parsing,
constrained reasoning, and consistency algorithms.

### Academic terminology

| Term | Meaning in Cacanode |
|---|---|
| Retrieval-Augmented Generation (RAG) | Retrieve tenant evidence before asking a generative model to answer |
| Dense retrieval | Semantic nearest-neighbor search over pretrained text-embedding vectors |
| Sparse retrieval | Lexical BM25 sparse-vector search, useful for identifiers, prices, and exact wording |
| Knowledge graph | Evidence-linked `Source`, `KnowledgeUnit`, and `Entity` nodes stored in Kuzu |
| Graph-assisted retrieval | Match query terms to graph entities and rank their evidence-bearing knowledge units |
| Multi-hop graph retrieval | Traverse grounded `RELATED_TO` paths in either direction and recover each edge's cited unit |
| Beam search | Retain a deterministic bounded set of the highest-scoring graph paths at each hop |
| Hybrid retrieval | Combine dense, sparse, and graph rankings rather than trusting one retrieval channel |
| Query routing | Deterministically classify a query into semantic, exact, relational, or calculation profiles |
| Rank fusion | Merge independently scored rankings through weighted Reciprocal Rank Fusion (RRF) |
| Reranking | Re-estimate query-document relevance for fused candidates with an optional cross-encoder |
| Context diversification | Limit early domination by one document before filling the final context window |
| Neighbor expansion | Restore nearby prose units after selecting high-relevance primary evidence |
| Grounding | Require answers and graph facts to retain traceable source-unit evidence |
| Provenance | Document, page, section, cell range, unit identity, and citation metadata attached to evidence |
| Semantic caching | Reuse a previously grounded answer for an embedding-similar query under strict scope guards |
| Cache equivalence | Degree to which a cached response and a fresh RAG response preserve meaning and citations |
| Abstention | Return an unavailable-information response instead of generating an unsupported answer |
| Neuro-symbolic execution | Use an LLM to select a validated operation, then execute it with deterministic code |
| Ablation study | Disable one pipeline component at a time to measure its individual contribution |

### Implementation classification

| Capability | Current status | Academic classification |
|---|---|---|
| Query-profile router | Implemented in project code | Deterministic AI/IR heuristic |
| Weighted dense, sparse, and graph fusion | Implemented in project code | Hybrid IR algorithm |
| Evidence diversity and neighbor expansion | Implemented in project code | Context-selection algorithm |
| Structural document and spreadsheet chunking | Implemented in project code | Document-representation algorithm |
| Revision-aware semantic answer cache | Implemented in project code | AI-systems and semantic-similarity algorithm |
| Grounded graph projection | Implemented in project code | Evidence-constrained knowledge representation |
| Bounded multi-hop relationship traversal | Implemented in project code | Deterministic graph-search and ranking algorithm |
| Spreadsheet calculation executor | Implemented in project code | Constrained neuro-symbolic reasoning |
| EmbeddingGemma inference | Pretrained model served locally | Model integration, not model training |
| Qdrant/FastEmbed BM25 | Pretrained/local sparse encoder | Model integration, not BM25 implementation from first principles |
| BGE cross-encoder reranking | Optional pretrained TEI service | Model integration, not reranker training |
| Generative LLM and graph-extraction LLM | Provider/model adapter | External inference dependency |
| Fine-tuning or adapter training | Not implemented | Proposed future work only |

### Formal hybrid-retrieval model

Let the normalized user query be $q$, and let the deterministic router assign one profile:

$$
p = r(q), \qquad
p \in \{\text{semantic},\text{exact},\text{relational},\text{calculation}\}.
$$

The router uses explicit precedence:

$$
\text{calculation} \succ \text{relational} \succ \text{exact} \succ \text{semantic}.
$$

This precedence prevents a query such as `Tính tổng giá cho mã "SKU-42"` from being incorrectly
treated as a simple exact-match query when its primary intent is calculation.

For each query, the pipeline obtains three ordered candidate rankings:

- $R_d(q)$: dense semantic retrieval from Qdrant;
- $R_s(q)$: BM25 sparse retrieval from Qdrant;
- $R_g(q)$: scoped entity seeding and bounded evidence-grounded path retrieval from Kuzu.

The current default profile weights are configuration values rather than learned parameters:

| Profile | Dense $w_d$ | Sparse $w_s$ | Graph $w_g$ | Intended behavior |
|---|---:|---:|---:|---|
| Semantic | 0.55 | 0.30 | 0.15 | Prefer conceptual similarity |
| Exact | 0.25 | 0.60 | 0.15 | Prefer lexical identifiers and literal values |
| Relational | 0.30 | 0.15 | 0.55 | Prefer entity-oriented evidence |
| Calculation | 0.35 | 0.50 | 0.15 | Prefer table rows, columns, and exact values |

The rankings are merged using **Weighted Reciprocal Rank Fusion**. For knowledge unit $u$, query
$q$, profile $p$, channel set $C=\{d,s,g\}$, channel weight $w_{p,c}$, and RRF constant
$k=30$:

$$
\mathrm{WRRF}(u \mid q,p)
= \sum_{c \in C}
\frac{w_{p,c}}{k + \mathrm{rank}_{c}(u)}.
$$

A unit absent from a channel contributes zero for that channel. Candidate identity is the tuple
`(document_id, unit_id)`; therefore, the same evidence returned by multiple channels accumulates
support instead of being duplicated. Ties are resolved deterministically by identity.

The implemented retrieval algorithm is:

1. Normalize and classify the query.
2. Generate the dense query vector and the sparse BM25 vector.
3. Execute dense, sparse, and bounded graph-path retrieval concurrently.
4. Treat a failed optional channel as an empty ranking and retain evidence from healthy channels.
5. Fuse at most 40 dense, 40 sparse, and 20 graph candidates into 30 WRRF candidates.
6. Optionally rerank fused candidates with `BAAI/bge-reranker-v2-m3`.
7. Select five primary units while applying a soft limit of two units per document.
8. Fill deferred candidates only when diversity-constrained selection is insufficient.
9. Add at most three adjacent eligible prose/page units, producing at most eight context units.
10. Generate an answer with one structured citation per supplied context unit.

```mermaid
flowchart LR
    Q[Query q] --> R[Deterministic profile router p = r q]
    R --> D[Dense ranking Rd]
    R --> S[Sparse ranking Rs]
    R --> G[Graph ranking Rg]

    D --> W[Weighted RRF]
    S --> W
    G --> W

    W --> X[Optional cross-encoder reranking]
    X --> V[Document-diverse primary evidence]
    V --> N[Section-aware neighbor expansion]
    N --> C[Grounded context with provenance]
    C --> A[LLM answer plus structured citations]
```

Primary implementation sources:

- Router, channel orchestration, WRRF, diversity, and neighbor expansion:
  [`retrieval.py`](rag-chatbot-fastapi/app/modules/retrieval/internal/retrieval.py)
- Dense and sparse Qdrant search:
  [`qdrant_search.py`](rag-chatbot-fastapi/app/modules/index/internal/qdrant_search.py)
- Optional TEI reranker:
  [`reranking.py`](rag-chatbot-fastapi/app/modules/retrieval/internal/reranking.py)
- Default candidate counts and profile weights:
  [`settings.py`](rag-chatbot-fastapi/app/bootstrap/settings.py)

### Structure-aware knowledge representation

The ingestion pipeline does not flatten every file into anonymous fixed-size strings. It constructs
typed `KnowledgeBlock` records with provenance such as section path, heading context, page number,
sheet name, cell range, table identity, and source offsets.

For prose longer than the configured character limit $L$, the chunker prefers a sentence or space
boundary. With overlap $O$, the next start position is:

$$
s_{i+1}=\max(e_i-O,\ s_i+1),
$$

where $s_i$ and $e_i$ are the current chunk's start and end positions. The `+1` term guarantees
progress even for adversarial input.

Structural content follows different rules:

| Block type | Chunking behavior | Reason |
|---|---|---|
| Paragraph/page/quote | Boundary-aware window with overlap | Preserve local linguistic continuity |
| Heading/list/code | Line-preserving split with zero character overlap | Avoid corrupting structural syntax |
| Markdown/HTML/DOCX table | Split by row lines and repeat the table header | Keep every table fragment interpretable |
| Spreadsheet table summary | Preserve schema, inferred types, sheet, and range | Support table discovery and calculation planning |
| Spreadsheet row | One provenance-bearing row representation | Support exact-value and aggregation retrieval |

Spreadsheet ingestion additionally identifies logical tables separated by blank row or column bands,
normalizes duplicate column names, infers primitive column types, records formula cells without
executing formulas, and creates deterministic table and unit identities.

```mermaid
flowchart TD
    File[PDF DOCX Markdown HTML TXT CSV XLSX] --> Parse[Format-specific structural parser]
    Parse --> Blocks[Typed knowledge blocks]
    Blocks --> Prose{Prose block?}
    Prose -->|yes| Window[Boundary-aware overlapping chunks]
    Prose -->|no| Structure[Line and row preserving chunks]
    Window --> Units[Deterministic knowledge units]
    Structure --> Units
    Units --> Provenance[Page section sheet cell range table and offsets]
    Provenance --> Encode[Dense and sparse encoding]
```

Implementation sources:

- Digital-format parsing and spreadsheet table discovery:
  [`extraction.py`](rag-chatbot-fastapi/app/modules/ingestion/internal/extraction.py)
- Deterministic structural chunking:
  [`chunking.py`](rag-chatbot-fastapi/app/modules/ingestion/internal/chunking.py)
- Ingestion coordination:
  [`pipeline.py`](rag-chatbot-fastapi/app/modules/ingestion/internal/pipeline.py)

### Evidence-grounded graph projection and bounded multi-hop retrieval

The graph layer stores an evidence-linked projection rather than an unconstrained graph generated
from model memory.

```mermaid
flowchart LR
    S[Source] -->|CONTAINS| U[KnowledgeUnit]
    U -->|MENTIONS evidence_unit_id| E1[Entity]
    E1 -->|RELATED_TO predicate evidence_unit_id| E2[Entity]
    U -. citation provenance .-> P[Page section sheet or cell range]
```

Entity and relation extraction is requested from a chat model, but project-owned validation enforces:

- normalized entity names;
- strict structured JSON;
- deduplication of repeated mentions and relations;
- an existing `evidence_unit_id` for every accepted item;
- relation subjects and objects that refer to accepted grounded entities;
- deterministic, tenant-scoped graph identities;
- idempotent replacement of one source projection.

Graph search is a deterministic project-owned algorithm. It does not call an LLM at query time. Text
is normalized with Unicode NFKC, case folding, and whitespace collapsing. Let $T(x)$ be the set of
Unicode word or hyphen tokens of length at least two. For entity $e$, its normalized name and aliases
form the entity token set $T(e)$. The lexical seed score is:

$$
L(e,q)
=\frac{|T(q)\cap T(e)|}{\max(1,|T(e)|)}
+I_{phrase}(e,q),
$$

where $I_{phrase}(e,q)=1$ when a normalized entity name or alias occurs as a complete query phrase,
and zero otherwise. Only the 32 highest-scoring seeds are retained.

For relationship predicate $r$, query-predicate overlap is:

$$
O(r,q)
=\frac{|T(q)\cap T(r)|}{\max(1,|T(r)|)}.
$$

For a cycle-free path $\pi_h=(e_0,r_1,e_1,\ldots,r_h,e_h)$ of length $h$, the path score is:

$$
P(\pi_h\mid q)
=L(e_0,q)\lambda^h\cdot
\left(1+\frac{\beta}{h}\sum_{i=1}^{h}O(r_i,q)\right),
\qquad \lambda=0.75,\quad \beta=0.25.
$$

Direct `MENTIONS` evidence is treated as $h=0$ with score $L(e_0,q)$. Each retained relationship
path contributes the unit named by the edge's `(source_id, evidence_unit_id)`. For evidence unit $u$,
let $m_u$ be the number of distinct grounded mention or relationship supports. Its graph-channel score
is:

$$
S(u,q)
=\max_{\pi\in\Pi(u)}P(\pi\mid q)\cdot
\left(1+\gamma\min(m_u-1,4)\right),
\qquad \gamma=0.05.
$$

The implementation performs separate outgoing and incoming one-hop Kuzu queries, so directed facts can
be discovered from either endpoint without using cycle-producing variable-length Cypher. At each hop it
deduplicates physical edges, rejects a neighbor already present in the path, reads at most 4,096 edges
per direction, and retains the best 256 path states. `GRAPH_MAX_HOPS` is validated in the range zero to
three: zero is the entity-only ablation, while the default retrieval pipeline uses three hops.

```mermaid
flowchart TD
    Q[Normalized query q] --> Scope[Tenant knowledge-base and document scope]
    Scope --> Seeds[Lexical entity and alias seeds top 32]
    Seeds --> Mentions[Direct MENTIONS evidence hop 0]
    Seeds --> Out[Outgoing RELATED_TO expansion]
    Seeds --> In[Incoming RELATED_TO expansion]
    Out --> Paths[Cycle-free scored path states top 256]
    In --> Paths
    Paths --> Ground{Current evidence unit exists and is visible?}
    Ground -->|yes| Units[Aggregate grounded unit support]
    Ground -->|no| Skip[Discard stale or out-of-scope edge]
    Mentions --> Rank[Stable evidence ranking]
    Units --> Rank
    Rank --> Limit[Apply graph candidate limit]
```

Document visibility is applied during seed selection, relationship expansion, and evidence recovery,
before the result limit. Results are deduplicated by `(document_id, unit_id)` and ties are resolved by
document, unit, and matched seed identity. Source replacement and deletion remove source-owned
`RELATED_TO` edges transactionally, while an entity shared by another source remains available.

No Kuzu schema migration is required. For an existing Kuzu volume, deploy the graph role before the AI
role and then run the existing one-shot Java `DocumentReindexCommand` for completed documents.
Replacing every source clears stale relationship rows created by the older lifecycle logic. Disable the
maintenance reindex flag again after all requests have been enqueued. Retrieval pipeline version 2
prevents pre-traversal retrieval or semantic-answer cache entries from being reused under the new
ranking semantics.

#### GraphRAG claim boundary

The repository now implements **bounded evidence-grounded multi-hop graph retrieval**. It does not
implement community detection, graph-community summaries, global-search GraphRAG, a learned entity
linker, or a trained neural path retriever. Seed selection, hop decay, predicate boost, beam width, and
support bonus are explicit heuristic parameters and must be reported as such in an academic paper.

Implementation sources:

- Grounded entity/relation extraction:
  [`entity_extraction.py`](rag-chatbot-fastapi/app/modules/ingestion/internal/entity_extraction.py)
- Deterministic seed scoring, bidirectional beam traversal, path ranking, and evidence aggregation:
  [`search.py`](rag-chatbot-fastapi/app/modules/graph/internal/search.py)
- Kuzu schema, transactional projection lifecycle, and graph query adapter:
  [`service.py`](rag-chatbot-fastapi/app/modules/graph/internal/service.py)
- Graph boundary types and evidence invariants:
  [`graph/api`](rag-chatbot-fastapi/app/modules/graph/api/__init__.py)
- Versioned traversal fixture and Kuzu behavior tests:
  [`test_graph_search.py`](rag-chatbot-fastapi/tests/test_graph_search.py) and
  [`graph_traversal_v1.json`](rag-chatbot-fastapi/tests/fixtures/graph_traversal_v1.json)

### Safety-constrained semantic answer caching

The semantic answer cache is not a plain query-vector lookup. It is a two-tier, revision-aware cache
that reuses only previously grounded answers under a canonical execution scope and a literal-sensitive
semantic guard.

```mermaid
flowchart TD
    Q[Eligible query] --> Scope[Build scope hash and guard hash]
    Scope --> Exact[Redis exact-query lookup]
    Exact -->|valid hit| ServeExact[Serve grounded exact answer]
    Exact -->|miss| Embed[Generate query embedding]
    Embed --> Semantic[Qdrant semantic candidate search]
    Semantic --> Guard{Same scope guard unexpired and similarity above threshold?}
    Guard -->|yes| Payload[Load and validate Redis answer payload]
    Payload -->|grounded| ServeSemantic[Serve grounded semantic answer]
    Guard -->|no| RAG[Run retrieval and generation]
    Payload -->|invalid or missing| RAG
    RAG --> Eligible{Answer has valid citations and no action or calculation?}
    Eligible -->|yes| Write[Write Redis payload and Qdrant query vector]
    Eligible -->|no| Return[Return without caching]
    Write --> Return
```

The canonical scope hash is:

$$
H_{scope}=\mathrm{SHA256}(\mathrm{CanonicalJSON}(S)),
$$

where $S$ includes tenant, chatbot, knowledge base, authoritative revision, channel, locale,
visible-document set, bounded conversation history, tenant prompt, prompt-schema version, LLM
configuration, embedding configuration, and retrieval-pipeline fingerprint.

The guard hash is:

$$
H_{guard}=\mathrm{SHA256}(p, N, V, D, C, I),
$$

where $p$ is the query profile and the remaining terms are normalized negations $N$, numbers
$V$, dates $D$, currencies $C$, and privacy-hashed identifiers $I$. Thus, semantically close
queries such as `after 7 days` and `after 14 days`, or `include archived plans` and `do not include
archived plans`, cannot share a candidate guard.

For query embedding $x$ and cached-query embedding $y$, cosine similarity is:

$$
\cos(x,y)=\frac{x\cdot y}{\lVert x\rVert_2\lVert y\rVert_2}.
$$

With the current threshold $\tau=0.97$, a semantic candidate is eligible only if:

$$
H_{scope}^{new}=H_{scope}^{cached}
\land H_{guard}^{new}=H_{guard}^{cached}
\land \cos(x,y)\ge\tau
\land t_{expiry}>t_{now}
\land \mathrm{GroundedPayloadValid}.
$$

`GroundedPayloadValid` requires a supported schema, matching revision and visibility scope, non-expired
content, valid structured citations, and at least one citation marker referenced by the answer.
Calculation queries, action requests, ticket drafts, ungrounded answers, and explicit no-information
answers are excluded from semantic-answer reuse.

In shadow mode, Cacanode compares a cache candidate with the fresh RAG response without serving the
candidate. Citation equivalence uses Jaccard overlap:

$$
J(C_{cache},C_{fresh})
=\frac{|C_{cache}\cap C_{fresh}|}{|C_{cache}\cup C_{fresh}|},
$$

while answer equivalence uses cosine similarity between ephemeral answer embeddings. These measurements
support threshold selection before enabling serve mode.

Implementation sources:

- Scope construction, guard extraction, semantic lookup, payload validation, and shadow comparison:
  [`semantic_answer_cache.py`](rag-chatbot-fastapi/app/modules/generation/internal/semantic_answer_cache.py)
- Exact/semantic cache placement in the RAG execution path:
  [`generation service.py`](rag-chatbot-fastapi/app/modules/generation/internal/service.py)
- Retrieval-configuration fingerprinting:
  [`retrieval cache.py`](rag-chatbot-fastapi/app/modules/retrieval/internal/cache.py)
- Operational design and rollout gates:
  [`docs/CACHING.md`](docs/CACHING.md)

The checked-in artifacts contain operational smoke evidence, not a controlled academic experiment:

| Run | Measured repeated-query p50 | Samples | Additional observation |
|---|---:|---:|---|
| [`RAG caches disabled`](rag-chatbot-fastapi/artifacts/rag-cache-disabled.json) | 3200.098 ms | 30 | Full embedding, retrieval, and LLM path |
| [`Semantic-answer serve`](rag-chatbot-fastapi/artifacts/semantic-answer-serve.json) | 12.362 ms | 5 | Five exact-tier hits |

The semantic-answer artifact records five avoided LLM requests, 5,440 avoided input tokens, and 1,650
avoided output tokens. The two runs have different sample counts and only demonstrate the order-of-
magnitude potential of exact reuse; they must not be reported as final semantic-cache effectiveness.
Use a matched query set, identical warm-up policy, threshold sweep, and equivalence labeling for the
paper's final results.

### Constrained neuro-symbolic spreadsheet reasoning

Spreadsheet questions use **constrained semantic parsing** rather than allowing generated code to run.

```mermaid
flowchart LR
    Q[Calculation question plus table schema] --> L[LLM planner]
    L --> J[Restricted JSON calculation command]
    J --> V[Pydantic schema and column validation]
    V --> P[Deterministic Polars execution]
    P --> R[Verified numeric or tabular result]
    R --> A[Grounded natural-language answer]
```

The planner may select only `count`, `sum`, `average`, `minimum`, `maximum`, `sort`, `top`, or
`bottom`, plus validated filters and optional grouping. The executor rejects unknown columns and
type-invalid filter values. It never evaluates generated Python, SQL, spreadsheet formulas, or arbitrary
expressions. This separation makes the LLM an intent parser while deterministic code remains the source
of computational truth.

Implementation sources:

- Planner coordination and restricted prompt contract:
  [`calculation.py`](rag-chatbot-fastapi/app/modules/generation/internal/calculation.py)
- Typed command model and deterministic execution:
  [`spreadsheets.py`](rag-chatbot-fastapi/app/modules/generation/internal/spreadsheets.py)

### Cross-service consistency and citation provenance

The Java API is not the retrieval algorithm, but it supplies academically relevant correctness
constraints for asynchronous AI execution.

Let $r_{request}$ be the knowledge-base revision captured before generation, $r_{response}$ the
revision echoed by FastAPI, and $r_{current}$ the revision observed before persistence. An answer is
accepted only when:

$$
r_{request}=r_{response}=r_{current}
\land D_{citation}\subseteq D_{completed}
\land (\mathrm{external}\Rightarrow D_{citation}\subseteq D_{visible}).
$$

If the knowledge base changes during inference, the Java control plane rebuilds the generation context
and retries once. Citations are rejected if their documents do not belong to the tenant and knowledge
base, are not completed, or are outside the customer-visible document set. The same revision and
visibility values participate in semantic-cache isolation, preventing stale or unauthorized reuse.

Implementation sources:

- Revision capture, retry, and final citation validation:
  [`ChatControlPlaneService.java`](api/src/main/java/com/cacanode/api/chat/query/ChatControlPlaneService.java)
- gRPC generation identity and revision validation:
  [`GrpcAiInferenceClient.java`](api/src/main/java/com/cacanode/api/ai/infrastructure/GrpcAiInferenceClient.java)
- Authoritative document visibility and citation checks:
  [`DocumentApiImpl.java`](api/src/main/java/com/cacanode/api/document/service/DocumentApiImpl.java)
- Knowledge-base revision increment:
  [`KnowledgeBaseRevisionService.java`](api/src/main/java/com/cacanode/api/tenant/service/KnowledgeBaseRevisionService.java)

### Paper-ready contribution statements

The report may accurately state the following implemented contributions:

1. **C1 — Query-adaptive hybrid retrieval.** Cacanode routes Vietnamese and English queries into four
   profiles and applies profile-specific weighted rank fusion over dense, sparse, and graph evidence.
2. **C2 — Structure-aware evidence representation.** The ingestion pipeline preserves document and
   spreadsheet structure and applies block-specific deterministic chunking instead of one universal
   fixed-window rule.
3. **C3 — Evidence-grounded multi-hop graph retrieval.** Extracted entities and relations are accepted
   only when they cite valid source units; deterministic bidirectional path search then ranks the
   relation evidence with hop decay, predicate overlap, and bounded support aggregation.
4. **C4 — Safety-constrained semantic caching.** Semantic answer reuse is isolated by authoritative
   execution scope and protected against negation, number, date, currency, identifier, visibility, and
   revision mismatches.
5. **C5 — Constrained neuro-symbolic calculation.** The LLM plans a restricted spreadsheet operation,
   while typed deterministic execution produces the authoritative result.
6. **C6 — Cross-service AI consistency.** Knowledge revision and citation visibility are revalidated
   before an answer is persisted or publicly exposed.

Academic claim boundaries:

| Accurate claim | Claim to avoid |
|---|---|
| Implemented and adapted weighted RRF for Cacanode's query profiles | Invented Reciprocal Rank Fusion |
| Designed a safety-constrained semantic-cache policy | Proved that semantic caching can never return a false hit |
| Implemented bounded evidence-grounded multi-hop traversal | Implemented community-based/global-search GraphRAG or a learned graph retriever |
| Locally serves pretrained embedding and sparse models | Trained EmbeddingGemma or BM25 from scratch |
| Integrates an optional pretrained BGE reranker | Fine-tuned the reranker in this repository |
| Uses an LLM for entity extraction and calculation planning | Implements a generative foundation model from scratch |
| Contains an evaluation harness and small fixture | Contains publication-grade retrieval results |

### Suggested paper title and structure

Suggested English title:

> **Adaptive Hybrid Retrieval with Evidence-Grounded Multi-Hop Graph Search and Safety-Constrained
> Semantic Caching for a Vietnamese Multi-Tenant RAG System**

Suggested Vietnamese title:

> **Truy xuất lai thích ứng với tìm kiếm đồ thị đa bước có căn cứ và bộ nhớ đệm ngữ nghĩa an toàn cho
> hệ thống RAG đa thuê bao tiếng Việt**

A teammate can map the implementation into the following paper structure:

1. **Introduction:** motivation, Vietnamese enterprise question answering, multi-tenant safety, research
   problem, objectives, and contribution summary.
2. **Background and related work:** RAG, dense and sparse retrieval, BM25, RRF, bounded multi-hop graph
   retrieval, reranking, semantic caching, grounding, and neuro-symbolic execution.
3. **Proposed method:** query router, weighted fusion, structural chunking, grounded graph projection,
   beam traversal and path ranking, semantic-cache guards, deterministic calculation, and
   revision/citation invariants.
4. **System implementation:** Spring control plane, FastAPI AI service, Qdrant, Kuzu, Redis, model
   adapters, data contracts, and source-code map.
5. **Experimental methodology:** dataset construction, train/validation/test separation when tuning,
   baselines, ablations, metrics, hardware, model versions, and reproducibility controls.
6. **Results and discussion:** retrieval quality, cache safety, latency/token savings, error analysis,
   Vietnamese-language behavior, and component trade-offs.
7. **Threats to validity and limitations:** small current fixtures, lexical graph seeding, fixed path
   parameters, heuristic router, unlearned fusion weights, provider dependence, and absence of
   fine-tuning code.
8. **Conclusion and future work:** learned entity linking or path scoring, community/global graph
   retrieval, learned routing or fusion weights, model/reranker fine-tuning, calibrated abstention,
   and larger evaluation data.

### Research questions

A paper based on the current implementation can study:

- **RQ1:** How much do sparse and graph evidence improve retrieval quality over dense retrieval alone
  for Vietnamese customer-support queries?
- **RQ2:** Does query-profile-specific WRRF outperform one fixed set of fusion weights?
- **RQ3:** Does structure-aware chunking improve Recall@K and citation precision compared with naïve
  fixed-size chunking?
- **RQ4:** At which semantic-cache threshold does answer-equivalence precision satisfy a safety target
  while still reducing latency and token usage?
- **RQ5:** How much do literal, negation, visibility, and revision guards reduce unsafe semantic-cache
  reuse?
- **RQ6:** On relational queries, how do `GRAPH_MAX_HOPS` values zero, one, two, and three affect
  Recall@K, MRR, nDCG@10, graph latency, and citation coverage?

### Required ablation study

Use the same held-out queries and knowledge-base snapshot for every condition:

| Experiment | Dense | Sparse | Graph | Router weights | Reranker | Diversity/neighbors | Purpose |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| A1 | Yes | No | No | N/A | No | No | Dense baseline |
| A2 | No | Yes | No | N/A | No | No | Lexical baseline |
| A3 | Yes | Yes | No | Fixed | No | No | Basic hybrid retrieval |
| A4 | Yes | Yes | Entity-only, $H=0$ | Fixed | No | No | Contribution of direct graph evidence |
| A5 | Yes | Yes | Entity-only, $H=0$ | Profile-specific | No | No | Contribution of query-adaptive fusion |
| A6 | Yes | Yes | Entity-only, $H=0$ | Profile-specific | Yes | No | Contribution of cross-encoder reranking |
| A7 | Yes | Yes | Entity-only, $H=0$ | Profile-specific | Optional | Yes | Full entity-only baseline |
| A8 | Yes | Yes | Multi-hop, $H=1,2,3$ | Profile-specific | Optional | Yes | Effect of bounded graph traversal |

For semantic caching, compare:

1. cache off;
2. exact cache only;
3. embedding similarity without guards;
4. similarity plus profile and literal guards;
5. full scope, revision, visibility, grounding, and expiry validation;
6. threshold values such as 0.90, 0.93, 0.95, 0.97, 0.98, and 0.99.

### Evaluation metrics

Let $Q^+=\{q\mid |Rel_q|>0\}$ be the answerable-query subset, $Rel_q$ the relevant unit set,
and $R_q^K$ the first $K$ retrieved units. The evaluator computes:

$$
\mathrm{Recall}@K
=\frac{1}{|Q^+|}\sum_{q\in Q^+}
\frac{|Rel_q\cap R_q^K|}{|Rel_q|}.
$$

For the rank of the first relevant result $rank_q$:

$$
\mathrm{MRR}
=\frac{1}{|Q^+|}\sum_{q\in Q^+}\frac{1}{rank_q}.
$$

With graded or binary relevance $rel_i$:

$$
\mathrm{DCG}@K=\sum_{i=1}^{K}\frac{rel_i}{\log_2(i+1)},
\qquad
\mathrm{nDCG}@K=\frac{\mathrm{DCG}@K}{\mathrm{IDCG}@K}.
$$

No-answer precision measures whether abstentions are justified:

$$
\mathrm{NoAnswerPrecision}
=\frac{\text{correct no-answer predictions}}
{\text{all no-answer predictions}}.
$$

Semantic-cache serving must prioritize equivalence precision over hit rate:

$$
\mathrm{CacheEquivalencePrecision}
=\frac{\text{served hits judged equivalent and citation-valid}}
{\text{all served semantic hits}}.
$$

Also report cache hit rate, false-hit rate, citation validity, citation Jaccard overlap, answer cosine
similarity, p50/p95 latency, avoided LLM calls, avoided input/output tokens, and Redis/Qdrant storage
footprint. Report 95% bootstrap confidence intervals and use paired query-level comparisons when
comparing retrieval variants.

### Dataset and reproducibility requirements

The checked-in Vietnamese retrieval fixture contains only five examples and is suitable for contract
testing, not final scientific conclusions. The versioned `graph_traversal_v1.json` fixture and its test
suite separately verify zero-to-three-hop, reverse-direction, alias, cycle, duplicate-edge, replacement,
and document-scope behavior against temporary Kuzu databases; they are correctness evidence, not a
publication-grade benchmark. A paper dataset should add a substantially larger, versioned, manually
reviewed query set with at least these strata:

- semantic paraphrases;
- exact identifiers, codes, dates, currencies, and numeric literals;
- relational entity questions;
- spreadsheet aggregation and filtering questions;
- Vietnamese with correct diacritics;
- Vietnamese without diacritics and with common spelling variants;
- Vietnamese-English code switching;
- adversarial negation and near-duplicate literal changes;
- unanswerable questions;
- customer-visibility and cross-tenant negative cases.

If fusion weights or cache thresholds are tuned, separate development/validation queries from the final
test set. Do not select parameters on the same examples used for the reported result. For the graph
ablation, record results independently for `GRAPH_MAX_HOPS=0`, `1`, `2`, and `3` while holding the
knowledge-base snapshot, graph candidate count, fusion weights, reranker, and context policy constant.

The existing evaluator can score recorded rankings:

```bash
cd rag-chatbot-fastapi
python -m app.maintenance.evaluate_retrieval \
  --dataset tests/data/retrieval_vi_v1.json \
  --results artifacts/full-pipeline.json \
  --label full-pipeline
```

Relevant verification suites can be run with:

```bash
cd rag-chatbot-fastapi
python -m pytest \
  tests/test_graph_search.py \
  tests/test_hybrid_retrieval.py \
  tests/test_semantic_answer_cache.py \
  tests/test_semantic_answer_cache_integration.py \
  tests/test_digital_formats.py \
  tests/test_compare_retrieval_results.py
```

The real semantic-cache integration test requires `REDIS_TEST_URL` and `QDRANT_TEST_URL`. Record the
exact Git commit, environment configuration, model identifiers, vector dimension, parser/chunker
versions, retrieval weights, graph hop count, graph-search constants, random seeds for any later learned
component, and dataset version with every reported experiment.

### Consolidated implementation-source map

| Academic component | Main implementation | Focused tests or evidence |
|---|---|---|
| Query routing and adaptive WRRF | [`retrieval.py`](rag-chatbot-fastapi/app/modules/retrieval/internal/retrieval.py) | [`test_hybrid_retrieval.py`](rag-chatbot-fastapi/tests/test_hybrid_retrieval.py) |
| Dense/sparse index search | [`qdrant_search.py`](rag-chatbot-fastapi/app/modules/index/internal/qdrant_search.py) | [`test_rag_chat.py`](rag-chatbot-fastapi/tests/test_rag_chat.py) |
| Sparse-model adapter | [`sparse.py`](rag-chatbot-fastapi/app/modules/model/internal/sparse.py) | [`test_hybrid_retrieval.py`](rag-chatbot-fastapi/tests/test_hybrid_retrieval.py) |
| Local embedding adapter and embedding cache | [`embedding.py`](rag-chatbot-fastapi/app/modules/model/internal/embedding.py) | [`test_embedding_cache.py`](rag-chatbot-fastapi/tests/test_embedding_cache.py) |
| Structural parsing | [`extraction.py`](rag-chatbot-fastapi/app/modules/ingestion/internal/extraction.py) | [`test_digital_formats.py`](rag-chatbot-fastapi/tests/test_digital_formats.py) |
| Structure-aware chunking | [`chunking.py`](rag-chatbot-fastapi/app/modules/ingestion/internal/chunking.py) | [`test_hybrid_retrieval.py`](rag-chatbot-fastapi/tests/test_hybrid_retrieval.py) |
| Grounded entity/relation extraction | [`entity_extraction.py`](rag-chatbot-fastapi/app/modules/ingestion/internal/entity_extraction.py) | [`test_digital_formats.py`](rag-chatbot-fastapi/tests/test_digital_formats.py) |
| Bounded graph traversal and path ranking | [`graph search.py`](rag-chatbot-fastapi/app/modules/graph/internal/search.py) | [`test_graph_search.py`](rag-chatbot-fastapi/tests/test_graph_search.py) and [`graph_traversal_v1.json`](rag-chatbot-fastapi/tests/fixtures/graph_traversal_v1.json) |
| Kuzu graph projection and lifecycle | [`graph service.py`](rag-chatbot-fastapi/app/modules/graph/internal/service.py) | [`test_digital_formats.py`](rag-chatbot-fastapi/tests/test_digital_formats.py) and [`test_graph_search.py`](rag-chatbot-fastapi/tests/test_graph_search.py) |
| Semantic answer cache | [`semantic_answer_cache.py`](rag-chatbot-fastapi/app/modules/generation/internal/semantic_answer_cache.py) | [`test_semantic_answer_cache.py`](rag-chatbot-fastapi/tests/test_semantic_answer_cache.py) |
| Real Redis/Qdrant cache path | [`semantic cache integration test`](rag-chatbot-fastapi/tests/test_semantic_answer_cache_integration.py) | Requires local integration services |
| Constrained spreadsheet execution | [`spreadsheets.py`](rag-chatbot-fastapi/app/modules/generation/internal/spreadsheets.py) | [`test_digital_formats.py`](rag-chatbot-fastapi/tests/test_digital_formats.py) |
| Retrieval metrics | [`evaluate_retrieval.py`](rag-chatbot-fastapi/app/maintenance/evaluate_retrieval.py) | [`retrieval_vi_v1.json`](rag-chatbot-fastapi/tests/data/retrieval_vi_v1.json) |
| Cache regression comparison | [`compare_retrieval_results.py`](rag-chatbot-fastapi/app/maintenance/compare_retrieval_results.py) | [`test_compare_retrieval_results.py`](rag-chatbot-fastapi/tests/test_compare_retrieval_results.py) |
| Knowledge revision and citation consistency | [`ChatControlPlaneService.java`](api/src/main/java/com/cacanode/api/chat/query/ChatControlPlaneService.java) | Java chat and document service tests |
| Public evidence provenance | [`PublicEvidenceService.java`](api/src/main/java/com/cacanode/api/document/service/PublicEvidenceService.java) | [`PublicEvidenceServiceTest.java`](api/src/test/java/com/cacanode/api/document/service/PublicEvidenceServiceTest.java) |

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
| `POST` | `/api/v1/billing/checkouts` | Create a server-priced PayOS Pro or Business checkout; tenant administrator only |
| `GET` | `/api/v1/billing/payments/{paymentId}` | Poll an internal payment status after returning from PayOS |
| `POST` | `/api/v1/billing/downgrade` | Schedule paid-plan fallback to Starter or immediately end a trial |
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
| Business monthly | 3,499,000 VND | 50,000 per month | 250 | 15 | 50 GB | Existing paid-plan features |
| Business annual | 34,990,000 VND | 50,000 per month | 250 | 15 | 50 GB | Same entitlements as monthly Business |
| Enterprise | Contact sales | Custom or unlimited | Custom or unlimited | Custom or unlimited | Custom | Sales-provisioned limits and features |

Hiring allowances are separate from support-platform storage and usage:

| Plan | Active jobs | Verified applications | Interview time | CV analyses | Recruitment storage |
|---|---:|---:|---:|---:|---:|
| Starter | 1 | 25/month | 0 | 0 | 50 MB |
| Trial | 1 | 25/trial | 20 minutes/trial | 5/trial | 100 MB |
| Pro | 3 | 150/month | 60 minutes/month | 100/month | 1 GB |
| Business | 10 | 1,000/month | 300 minutes/month | 500/month | 10 GB |
| Enterprise | Contracted | Contracted | Contracted | Contracted | Contracted |

Existing Enterprise platform limits may remain nullable. Enterprise hiring limits are explicit numeric values in the subscription snapshot and default to zero until contract provisioning, so missing or unprovisioned hiring allowances fail closed.

### Subscription lifecycle

- Registration creates a 14-day Pro trial in the same transaction as the tenant account.
- Trial expiration moves directly to Starter without a grace period.
- A paid monthly term uses one calendar month from activation; an annual term uses one year.
- Annual subscriptions retain monthly message and hiring windows anchored to the original paid activation date.
- PayOS does not provide recurring subscription mandates, so renewals use new hosted payment links.
- Same-plan renewal extends from the current `paidThroughAt` value and retains the quota anchor, including during grace.
- Pro ↔ Business changes activate immediately after verified payment with a fresh term and quota anchor. Unused prepaid time is not credited or prorated.
- Paid expiration enters a three-day grace period. Grace retains the final quota window and does not grant another allowance.
- After grace, the tenant falls back to Starter. Existing documents, users, webhook configuration, and branding preferences remain stored.
- Choosing Starter during paid Pro or Business schedules fallback after prepaid access and grace. Choosing Starter during trial ends the trial immediately.
- Version 1 does not provide refunds, prorating, automatic renewal, or automatic resource deletion.

### PayOS payment flow

Only `TENANT_ADMIN` users may create Pro or Business checkouts. The server resolves the requested plan amount and entitlement snapshot from the catalog, allocates the PayOS order code from a database sequence, and creates a payment link that expires after 30 minutes. `Idempotency-Key` is supported for checkout creation.

The browser return and cancel URLs control presentation only. On a PayOS return, the frontend polls CacaNode's payment-status endpoint; each open-payment read reconciles against PayOS immediately and never treats return query parameters as proof of payment.

Webhook activation is verified through the pinned `vn.payos:payos-java:2.0.1` SDK. The application confirms `PAYOS_WEBHOOK_URL` with PayOS at startup and before checkout creation. Webhook and reconciliation processing check the order code, payment-link ID, VND currency or paid amount, and expected amount. Mismatches move the order to `REVIEW` and never activate entitlements. Successful duplicate processing is idempotent and does not extend the subscription twice.

Pending payments are also reconciled against PayOS every five minutes as a background fallback. Rate-limit and server failures receive bounded retries and emit PayOS billing metrics.

### Quota and feature enforcement

- Message usage is stored in billing-anniversary `usage_metrics` periods. The Spring billing service locks the tenant and atomically increments the applicable period row before invoking FastAPI inference.
- Reaching the message limit returns the existing `MESSAGE_QUOTA_EXCEEDED` response with HTTP `429`.
- Document uploads lock the tenant entitlement row and reject before object storage when document count or storage would exceed the limit.
- Team-member limits count active members plus unexpired pending invitations and apply to invitations, acceptance, and reactivation.
- Downgrades preserve existing resources but block additional messages, uploads, invitations, and reactivations while usage exceeds Starter limits.
- Hiring application and CV charges use globally idempotent ledgers. Active jobs, recruitment storage, and interview capacity use reservation ledgers serialized by the tenant subscription lock.
- Pending recruitment-storage and interview reservations expire after 24 hours and are reaped every five minutes. Committed storage and active-job reservations do not expire.
- Plan changes preserve committed hiring usage and reservations. New growth is blocked when usage plus reservations exceeds the new allowance, while release and incurred interview settlement remain permitted.
- Starter widget tokens remain usable. Creating or using `api:chat` tokens requires the API-access entitlement.
- Webhook endpoint configuration is preserved after downgrade, while create, test, secret rotation, and delivery are disabled.
- Starter retains dashboard summary analytics; detailed analytics require Trial, Pro, or Enterprise.
- Saved branding preference is preserved, but CacaNode branding is forced whenever custom branding is disabled.

Quota-warning notifications are emitted once per period at 80%, and quota-exceeded notifications are emitted at 100%. Paid renewal notices are created seven, three, and one day before expiration and daily during grace unless a Starter downgrade is already scheduled.

### Persistence and module boundaries

- `billing_subscriptions` stores one subscription per tenant, lifecycle timestamps, reminder state, catalog version, optimistic version, and a complete entitlement snapshot.
- `billing_payment_orders` stores internal payment IDs, tenant and user IDs, sequence-generated PayOS order codes, server-resolved prices, provider link data, status, and purchase snapshots.
- `billing_webhook_events` stores payload hashes and processing results without retaining unnecessary counterparty banking details.
- `hiring_quota_consumptions` stores idempotent application and CV charges across quota-window resets.
- `hiring_quota_reservations` stores active-job, recruitment-storage, and interview reservation lifecycles.
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
(:Entity)-[:RELATED_TO {predicate, evidence_unit_id, source_id}]->(:Entity)
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

> **Implementation status:** this section specifies proposed adaptation, data-governance, and promotion
> policy. The current repository does not contain a model-training or fine-tuning pipeline.

Fine-tuning would change model behavior, while retrieval supplies current tenant facts. Tenant
documents are not automatically converted into training data.

### Proposed Gemma 4 adaptation

A future Gemma adapter may be trained for:

- Natural Vietnamese customer-support language.
- Correct diacritics and punctuation.
- Text without diacritics and common misspellings.
- Regional expressions when supported by the approved dataset.
- Vietnamese-English technical code-switching.
- Grounded answering from supplied context.
- Explicit unavailable answers when context is insufficient.
- Structured JSON for query routing and graph extraction.
- Stable citation behavior.

The proposed parameter-efficient fine-tuning path would use LoRA or QLoRA. Base checkpoints would
remain immutable, and serving would reference an approved base-model version and adapter version.

### Proposed EmbeddingGemma adaptation

Embedding fine-tuning would use Vietnamese query, positive, and hard-negative examples.

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

### Future model-promotion policy

A future trained model or adapter would be served only after passing:

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
- Tenant-owned cache keys include trusted tenant scope; free-form identities are canonicalized and hashed.
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
- Redis-backed rate limiting and optional caches fail open; PostgreSQL, Qdrant, Kuzu, and model services remain authoritative.

### Observability

The platform exports:

- Request rate, latency, status, and active gRPC generations.
- Time to first token and tokens per second.
- Retrieval latency and result counts by modality.
- Qdrant and Kuzu latency.
- Queue depth, retry count, and dead-letter count.
- Ingestion duration by file type and stage.
- GPU memory, utilization, batch size, and model queue time.
- Tenant usage counters without raw message content.
- Cache hit, miss, bypass, write, invalidation, error, latency, and payload-size metrics using controlled cache names.
- Raw Redis operation outcomes for cache and rate-limit components without keys, tokens, tenant IDs, queries, or exception messages.
- Cache authoritative-call counts and latency through `cacanode_cache_authoritative_seconds{service,cache,outcome}`.
- Process-local stampede observations through
  `cacanode_cache_authoritative_loads_in_flight`,
  `cacanode_cache_same_key_overlaps_total`, and
  `cacanode_cache_same_key_concurrency`. These metrics use only controlled service/cache labels;
  tracker state contains SHA-256 key digests and is removed after every load.

Logs are structured JSON and include `request_id`, `tenant_id`, `chatbot_id`, and `session_id` where permitted.

---

## Configuration

Create `.env` from `.env.example`. Production values are provided by the deployment secret store.
Authentication uses the existing HS256 JWT setup: Spring signs access and verification tokens with `TOKEN_KEY`; refresh tokens remain opaque server-stored values, and `EXPIRY_DAYS` controls their cookie/storage lifetime.

All optional caches are default-off. Integration-token authentication and `last_used_at` updates
are authoritative Spring transactions. A Spring business cache requires `CACHE_ENABLED`,
`BUSINESS_READ_CACHE_ENABLED`, and its
domain flag. Embedding and retrieval caching require their respective FastAPI flags. PostgreSQL,
Ollama, and the hybrid retrieval pipeline remain authoritative, and Redis errors fail open.

The repository also includes guarded stampede/cold-start evidence tooling, but no lock,
coalescing, prewarming, or refresh job. Destructive cold runs are restricted to loopback Redis
database 15 with an affirmative CLI flag. See the [caching guide](docs/CACHING.md) for exact
configuration and rollout gates.

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
REDIS_CONNECT_TIMEOUT_SECONDS=1
REDIS_OPERATION_TIMEOUT_SECONDS=1
CACHE_ENABLED=false
CACHE_KEY_PREFIX=ccn:v1
CACHE_TTL_JITTER_PERCENT=10
BUSINESS_READ_CACHE_ENABLED=false
WIDGET_CONFIG_CACHE_ENABLED=false
WIDGET_CONFIG_CACHE_TTL_SECONDS=120
CUSTOMER_ANSWER_PROMPT_CACHE_ENABLED=false
CUSTOMER_ANSWER_PROMPT_CACHE_TTL_SECONDS=120
BILLING_ACCOUNT_CACHE_ENABLED=false
BILLING_ACCOUNT_CACHE_TTL_SECONDS=30
WORKSPACE_CACHE_ENABLED=false
WORKSPACE_CACHE_TTL_SECONDS=300
DASHBOARD_CACHE_ENABLED=false
DASHBOARD_CACHE_TTL_SECONDS=20
ANALYTICS_CACHE_ENABLED=false
ANALYTICS_CACHE_TTL_SECONDS=60
USER_DIRECTORY_CACHE_ENABLED=false
USER_DIRECTORY_CACHE_TTL_SECONDS=30
DOCUMENT_LIST_CACHE_ENABLED=false
DOCUMENT_LIST_CACHE_TTL_SECONDS=15
EMBEDDING_CACHE_ENABLED=false
EMBEDDING_CACHE_TTL_SECONDS=86400
RETRIEVAL_CACHE_ENABLED=false
RETRIEVAL_CACHE_TTL_SECONDS=120
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
BILLING_CATALOG_VERSION=2026-07-23
HIRING_RESERVATION_TTL_HOURS=24
HIRING_RESERVATION_REAPER_MS=300000

# Observability
LOG_LEVEL=INFO
OTEL_EXPORTER_OTLP_ENDPOINT=
DISABLE_EXTERNAL_TELEMETRY=true

# Internal Spring -> FastAPI gRPC (plaintext only for local development)
AI_GRPC_TARGET=localhost:50051
AI_GRPC_PLAINTEXT=true
GENERATION_RESULT_CACHE_TTL_SECONDS=600
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
SeaweedFS, and the Kuzu graph service through Docker Compose. The internal FastAPI inference app runs on the
host with auto-reload:

```bash
cd rag-chatbot-fastapi
cp .env.example .env
make dev
```

Spring reaches inference on `localhost:50051`, and inference reaches Graph on
`http://localhost:8010`. Reranking is disabled by default locally
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
reranker. Follow the [deployment guide](docs/DEPLOYMENT.md) for DNS, HTTPS, droplet bootstrap, GitHub Actions,
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
| `/health/ready` | Service-specific readiness state; checks differ by service |
| `/metrics` | Private Prometheus-compatible metrics endpoint |

AI API readiness reports model configuration and worker state. It intentionally does not ping Redis; Redis-backed rate limiting and the disabled cache infrastructure remain fail-open.

---

## Testing and Release Gates

### Required automated tests

- Authentication and refresh-token rotation.
- Role and scope enforcement.
- Tenant isolation across PostgreSQL, Qdrant, Kuzu, SeaweedFS, Redis, and queues.
- Cache key construction, raw-byte preservation, TTL jitter, bypass, hit, miss, write, invalidation, metrics, and Redis-error fallback.
- FastAPI Redis lifecycle reuse, no startup ping, one-time shutdown close, rate-limit `429`, and connection/timeout fail-open behavior.
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

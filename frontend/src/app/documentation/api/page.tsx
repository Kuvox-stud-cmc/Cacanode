import { BulletList, Callout, CodeBlock, DocArticle, DocSection, Endpoint, FeatureBadge, ParameterTable } from "@/components/documentation/DocumentationComponents"

const createSession = `curl -X POST "API_BASE_URL/api/v1/external/chat/sessions" \\
  -H "Authorization: Bearer CACANODE_API_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{
    "external_user_id": "user_123",
    "customer_name": "Example User",
    "customer_email": "user@example.com",
    "locale": "en-US",
    "metadata": { "plan": "example" }
  }'`

const sendMessage = `curl -X POST "API_BASE_URL/api/v1/external/chat/sessions/SESSION_ID/messages" \\
  -H "Authorization: Bearer CACANODE_API_TOKEN" \\
  -H "Content-Type: application/json" \\
  -H "Idempotency-Key: UNIQUE_OPERATION_ID" \\
  -H "X-Request-ID: REQUEST_TRACE_ID" \\
  -d '{
    "content": "How do I update my billing details?",
    "metadata": { "surface": "help-center" }
  }'`

const messageResponse = `{
  "role": "assistant",
  "content": "...",
  "citations": [{
    "id": "CITATION_ID",
    "document_id": "DOCUMENT_ID",
    "source_name": "billing-guide.pdf",
    "page_number": 4,
    "chunk_index": 12,
    "score": 0.91,
    "snippet": "...",
    "unit_id": "UNIT_ID",
    "modality": "text",
    "section_path": ["Billing", "Payment details"],
    "block_type": "paragraph",
    "sheet_name": null,
    "cell_range": null,
    "table_id": null,
    "public_url": "SIGNED_TEMPORARY_EVIDENCE_URL"
  }],
  "action": null
}`

const ticket = `curl -X POST "API_BASE_URL/api/v1/external/tickets" \\
  -H "Authorization: Bearer CACANODE_API_TOKEN" \\
  -H "Content-Type: application/json" \\
  -H "Idempotency-Key: UNIQUE_TICKET_OPERATION_ID" \\
  -d '{
    "sessionId": "SESSION_ID",
    "customerEmail": "user@example.com",
    "customerName": "Example User",
    "title": "Billing help",
    "description": "I need help updating my billing details."
  }'`

export default function ApiDocumentationPage() {
  return <DocArticle title="Custom Chat API" description="Create customer conversations from your own server, UI, or workflow while CacaNode handles retrieval, generation, citations, and support handoff.">
    <Callout type="note"><FeatureBadge kind="pro">Pro</FeatureBadge> Creating or using an <code>api:chat</code> token requires Pro. Token management is tenant administrator-only.</Callout>
    <DocSection id="authentication" title="Authentication">
      <p>Create an integration token in <strong>Settings → Tokens</strong> with the <code>api:chat</code> scope. Send it on every request:</p>
      <CodeBlock language="http" code="Authorization: Bearer CACANODE_API_TOKEN" />
      <Callout type="security">Use the Custom API from trusted server code. Never place an <code>api:chat</code> token in a browser bundle, mobile binary, URL, log entry, or source repository.</Callout>
    </DocSection>
    <DocSection id="create-session" title="Create a session">
      <Endpoint method="POST" path="/api/v1/external/chat/sessions" />
      <ParameterTable parameters={[{ name: "external_user_id", type: "string ≤255", description: "Your stable customer identifier." }, { name: "customer_name", type: "string ≤255", description: "Customer display name." }, { name: "customer_email", type: "email ≤320", description: "Validated customer email." }, { name: "locale", type: "string ≤20", description: "Conversation locale; the workspace default is used when omitted." }, { name: "metadata", type: "object", description: "Application-defined customer context." }]} />
      <CodeBlock language="bash" code={createSession} />
      <p>The response uses snake_case identifiers: <code>id</code>, <code>chatbot_id</code>, <code>knowledge_base_id</code>, <code>tenant_id</code>, and <code>locale</code>.</p>
    </DocSection>
    <DocSection id="send-message" title="Send a message">
      <Endpoint method="POST" path="/api/v1/external/chat/sessions/{sessionId}/messages" />
      <ParameterTable parameters={[{ name: "content", type: "string ≤32000", required: true, description: "Non-empty customer message." }, { name: "metadata", type: "object", description: "Optional per-message application context." }]} />
      <CodeBlock language="bash" code={sendMessage} />
      <CodeBlock language="json" code={messageResponse} />
    </DocSection>
    <DocSection id="history-close" title="Read history and close">
      <Endpoint method="GET" path="/api/v1/external/chat/sessions/{sessionId}/messages" />
      <p>History returns up to 50 messages in sequence order. Each item contains <code>role</code>, <code>content</code>, <code>citations</code>, optional <code>sequence_number</code>, and optional <code>action</code>.</p>
      <Endpoint method="DELETE" path="/api/v1/external/chat/sessions/{sessionId}" />
      <p>Closing is idempotent and returns <code>204 No Content</code>. A closed customer conversation appears as closed in the support workspace and emits <code>conversation.closed</code>.</p>
    </DocSection>
    <DocSection id="idempotency" title="Idempotency and request tracing">
      <BulletList><li>Send a unique <code>Idempotency-Key</code> for each message creation and ticket creation operation.</li><li>Reuse the same key only when retrying the same logical operation after a timeout or transport failure.</li><li>Send <code>X-Request-ID</code> on message requests to correlate application and CacaNode logs.</li><li>Session creation does not currently accept an idempotency header; avoid automatically replaying it unless your integration reconciles the returned session ID.</li></BulletList>
    </DocSection>
    <DocSection id="tickets" title="Create a support ticket">
      <Endpoint method="POST" path="/api/v1/external/tickets" />
      <p>A ticket must reference a session authorized by the same integration context. The external ticket DTO uses camelCase fields.</p>
      <CodeBlock language="bash" code={ticket} />
    </DocSection>
    <DocSection id="citations" title="Citations and public evidence">
      <p>Citation fields are snake_case and can describe text, pages, sections, spreadsheet ranges, and tables. Fields that do not apply are omitted or null.</p>
      <Callout type="security"><code>public_url</code> is a signed, temporary web link. It is issued only for completed <code>CUSTOMER_AND_EMPLOYEE</code> documents and remains usable only while the integration token, workspace, chatbot, knowledge base, and document are eligible. Treat it as customer-accessible evidence, not a permanent document URL.</Callout>
    </DocSection>
  </DocArticle>
}

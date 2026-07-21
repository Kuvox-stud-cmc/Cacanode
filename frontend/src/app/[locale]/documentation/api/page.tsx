import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"

import { BulletList, Callout, CodeBlock, DocArticle, DocSection, Endpoint, FeatureBadge, ParameterTable } from "@/components/documentation/DocumentationComponents"
import type { AppLocale } from "@/i18n/routing"
import { documentationPage } from "@/lib/documentation"


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

type PageProps = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale } = await params
  const page = documentationPage("/documentation/api", locale as AppLocale)
  return { title: page.title, description: page.description }
}

export default async function ApiDocumentationPage({ params }: PageProps) {
  const { locale } = await params
  const t = await getTranslations({ locale: locale as AppLocale, namespace: "DocumentationContent.api" })
  const page = documentationPage("/documentation/api", locale as AppLocale)
  const sections = Object.fromEntries(page.sections.map((section) => [section.id, section.title]))

  return <DocArticle title={page.title} description={page.description}>
    <Callout type="note"><FeatureBadge kind="pro">Pro</FeatureBadge> {t.rich("proNote", { code: (chunks) => <code>{chunks}</code> })}</Callout>
    <DocSection id="authentication" title={sections.authentication}>
      <p>{t.rich("authenticationBody", { strong: (chunks) => <strong>{chunks}</strong>, code: (chunks) => <code>{chunks}</code> })}</p>
      <CodeBlock language="http" code="Authorization: Bearer CACANODE_API_TOKEN" />
      <Callout type="security">{t.rich("authenticationSecurity", { code: (chunks) => <code>{chunks}</code> })}</Callout>
    </DocSection>
    <DocSection id="create-session" title={sections["create-session"]}>
      <Endpoint method="POST" path="/api/v1/external/chat/sessions" />
      <ParameterTable parameters={[{ name: "external_user_id", type: "string ≤255", description: t("externalUserDescription") }, { name: "customer_name", type: "string ≤255", description: t("customerNameDescription") }, { name: "customer_email", type: "email ≤320", description: t("customerEmailDescription") }, { name: "locale", type: "string ≤20", description: t("localeDescription") }, { name: "metadata", type: "object", description: t("metadataDescription") }]} />
      <CodeBlock language="bash" code={createSession} />
      <p>{t.rich("sessionResponse", { code: (chunks) => <code>{chunks}</code> })}</p>
    </DocSection>
    <DocSection id="send-message" title={sections["send-message"]}>
      <Endpoint method="POST" path="/api/v1/external/chat/sessions/{sessionId}/messages" />
      <ParameterTable parameters={[{ name: "content", type: "string ≤32000", required: true, description: t("contentDescription") }, { name: "metadata", type: "object", description: t("messageMetadataDescription") }]} />
      <CodeBlock language="bash" code={sendMessage} />
      <CodeBlock language="json" code={messageResponse} />
    </DocSection>
    <DocSection id="history-close" title={sections["history-close"]}>
      <Endpoint method="GET" path="/api/v1/external/chat/sessions/{sessionId}/messages" />
      <p>{t.rich("historyBody", { code: (chunks) => <code>{chunks}</code> })}</p>
      <Endpoint method="DELETE" path="/api/v1/external/chat/sessions/{sessionId}" />
      <p>{t.rich("closeBody", { code: (chunks) => <code>{chunks}</code> })}</p>
    </DocSection>
    <DocSection id="idempotency" title={sections.idempotency}>
      <BulletList><li>{t.rich("idempotencyUnique", { code: (chunks) => <code>{chunks}</code> })}</li><li>{t("idempotencyReuse")}</li><li>{t.rich("requestId", { code: (chunks) => <code>{chunks}</code> })}</li><li>{t("sessionReplay")}</li></BulletList>
    </DocSection>
    <DocSection id="tickets" title={sections.tickets}>
      <Endpoint method="POST" path="/api/v1/external/tickets" />
      <p>{t("ticketBody")}</p>
      <CodeBlock language="bash" code={ticket} />
    </DocSection>
    <DocSection id="citations" title={sections.citations}>
      <p>{t("citationsBody")}</p>
      <Callout type="security">{t.rich("citationsSecurity", { code: (chunks) => <code>{chunks}</code> })}</Callout>
    </DocSection>
  </DocArticle>
}

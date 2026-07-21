import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"

import { BulletList, Callout, CodeBlock, DocArticle, DocSection, FeatureBadge, ParameterTable } from "@/components/documentation/DocumentationComponents"
import type { AppLocale } from "@/i18n/routing"
import { documentationPage } from "@/lib/documentation"


const envelope = `{
  "id": "EVENT_ID",
  "type": "conversation.started",
  "createdAt": "2026-07-20T10:00:00",
  "data": {
    "conversationId": "CONVERSATION_ID",
    "chatbotId": "CHATBOT_ID",
    "channel": "CUSTOM_API",
    "externalUserId": "user_123"
  }
}`

const verify = `import { createHmac, timingSafeEqual } from "node:crypto";

export function verifyCacaNodeWebhook(rawBody, headers) {
  const timestamp = headers["x-cacanode-timestamp"];
  const supplied = headers["x-cacanode-signature"];
  if (!timestamp || !supplied?.startsWith("v1=")) return false;

  const expectedHex = createHmac("sha256", process.env.CACANODE_WEBHOOK_SECRET)
    .update(timestamp + "." + rawBody)
    .digest("hex");
  const expected = Buffer.from(expectedHex, "hex");
  const actual = Buffer.from(supplied.slice(3), "hex");

  return expected.length === actual.length && timingSafeEqual(expected, actual);
}`

type PageProps = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale } = await params
  const page = documentationPage("/documentation/webhooks", locale as AppLocale)
  return { title: page.title, description: page.description }
}

export default async function WebhooksDocumentationPage({ params }: PageProps) {
  const { locale } = await params
  const t = await getTranslations({ locale: locale as AppLocale, namespace: "DocumentationContent.webhooks" })
  const page = documentationPage("/documentation/webhooks", locale as AppLocale)
  const sections = Object.fromEntries(page.sections.map((section) => [section.id, section.title]))

  return <DocArticle title={page.title} description={page.description}>
    <DocSection id="requirements" title={sections.requirements}>
      <p><FeatureBadge kind="pro">Pro</FeatureBadge> <FeatureBadge kind="admin">{t("adminBadge")}</FeatureBadge> {t("requirementsBody")}</p>
      <ParameterTable parameters={[{ name: "conversation.started", type: t("eventType"), description: t("startedDescription") }, { name: "conversation.closed", type: t("eventType"), description: t("closedDescription") }, { name: "ticket.created", type: t("eventType"), description: t("ticketDescription") }]} />
      <Callout type="security">{t("destinationSecurity")}</Callout>
    </DocSection>
    <DocSection id="payload" title={sections.payload}>
      <CodeBlock language="json" code={envelope} />
      <BulletList><li>{t.rich("idDescription", { code: (chunks) => <code>{chunks}</code> })}</li><li>{t.rich("typeDescription", { code: (chunks) => <code>{chunks}</code> })}</li><li>{t.rich("createdDescription", { code: (chunks) => <code>{chunks}</code> })}</li><li>{t.rich("dataDescription", { code: (chunks) => <code>{chunks}</code> })}</li></BulletList>
      <p>{t.rich("ticketData", { code: (chunks) => <code>{chunks}</code> })}</p>
    </DocSection>
    <DocSection id="verify" title={sections.verify}>
      <p>{t.rich("signatureBody", { code: (chunks) => <code>{chunks}</code> })}</p>
      <CodeBlock language="javascript" code={verify} />
      <Callout type="warning">{t("signatureWarning")}</Callout>
    </DocSection>
    <DocSection id="delivery" title={sections.delivery}>
      <p>{t.rich("deliveryBody", { strong: (chunks) => <strong>{chunks}</strong> })}</p>
      <BulletList><li>{t("attempts")}</li><li>{t("delays")}</li><li>{t("allEndpoints")}</li><li>{t("idempotent")}</li></BulletList>
    </DocSection>
    <DocSection id="testing-rotation" title={sections["testing-rotation"]}>
      <p>{t.rich("testingBody", { strong: (chunks) => <strong>{chunks}</strong>, code: (chunks) => <code>{chunks}</code> })}</p>
      <Callout type="security">{t("rotationSecurity")}</Callout>
    </DocSection>
  </DocArticle>
}

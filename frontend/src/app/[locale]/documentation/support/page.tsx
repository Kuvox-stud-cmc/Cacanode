import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"

import { BulletList, Callout, DocArticle, DocSection, FeatureBadge, ParameterTable, Step, Steps } from "@/components/documentation/DocumentationComponents"
import type { AppLocale } from "@/i18n/routing"
import { documentationPage } from "@/lib/documentation"

type PageProps = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale } = await params
  const page = documentationPage("/documentation/support", locale as AppLocale)
  return { title: page.title, description: page.description }
}

export default async function SupportDocumentationPage({ params }: PageProps) {
  const { locale } = await params
  const t = await getTranslations({ locale: locale as AppLocale, namespace: "DocumentationContent.support" })
  const page = documentationPage("/documentation/support", locale as AppLocale)
  const sections = Object.fromEntries(page.sections.map((section) => [section.id, section.title]))

  return <DocArticle title={page.title} description={page.description}>
    <DocSection id="conversations" title={sections.conversations}>
      <p>{t("conversationsBody")}</p>
    </DocSection>
    <DocSection id="channels" title={sections.channels}>
      <ParameterTable parameters={[
        { name: "WIDGET", type: t("channelType"), description: t("widgetDescription") },
        { name: "CUSTOM_API", type: t("channelType"), description: t("apiDescription") },
        { name: "OPEN", type: t("statusType"), description: t("openDescription") },
        { name: "CLOSED", type: t("statusType"), description: t("closedDescription") },
      ]} />
      <p>{t.rich("closingBody", { code: (chunks) => <code>{chunks}</code> })}</p>
    </DocSection>
    <DocSection id="tickets" title={sections.tickets}>
      <BulletList>
        <li><strong>{t("statusField")}</strong> <code>OPEN</code>, <code>IN_PROGRESS</code>, <code>RESOLVED</code>, {t("orConnector")} <code>CLOSED</code>.</li>
        <li><strong>{t("priorityField")}</strong> <code>LOW</code>, <code>NORMAL</code>, <code>HIGH</code>, {t("orConnector")} <code>URGENT</code>.</li>
        <li><strong>{t("sourceField")}</strong> <code>WIDGET</code> {t("orConnector")} <code>CUSTOM_API</code>.</li>
        <li><strong>{t("assigneeField")}</strong> {t("assigneeText")}</li>
        <li><strong>{t("notesField")}</strong> {t("notesText")}</li>
      </BulletList>
    </DocSection>
    <DocSection id="workflow" title={sections.workflow}>
      <Steps>
        <Step title={t("openTitle")}>{t("openText")}</Step>
        <Step title={t("triageTitle")}>{t("triageText")}</Step>
        <Step title={t("workTitle")}>{t.rich("workText", { code: (chunks) => <code>{chunks}</code> })}</Step>
        <Step title={t("closeTitle")}>{t.rich("closeText", { code: (chunks) => <code>{chunks}</code> })}</Step>
      </Steps>
      <Callout type="note">{t("lifecycleNote")}</Callout>
    </DocSection>
    <DocSection id="filters" title={sections.filters}>
      <p>{t("filtersBody")}</p>
      <Callout type="security"><FeatureBadge>{t("internalBadge")}</FeatureBadge> {t("notesSecurity")}</Callout>
    </DocSection>
  </DocArticle>
}

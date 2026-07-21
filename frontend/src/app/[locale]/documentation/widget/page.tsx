import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"

import { BulletList, Callout, CodeBlock, DocArticle, DocSection, FeatureBadge, RelatedLinks, Step, Steps } from "@/components/documentation/DocumentationComponents"
import type { AppLocale } from "@/i18n/routing"
import { documentationPage } from "@/lib/documentation"

const embed = `<script
  async
  src="WIDGET_SCRIPT_URL"
  data-token="CACANODE_WIDGET_TOKEN"
></script>`

type PageProps = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale } = await params
  const page = documentationPage("/documentation/widget", locale as AppLocale)
  return { title: page.title, description: page.description }
}

export default async function WidgetDocumentationPage({ params }: PageProps) {
  const { locale } = await params
  const t = await getTranslations({ locale: locale as AppLocale, namespace: "DocumentationContent.widget" })
  const page = documentationPage("/documentation/widget", locale as AppLocale)
  const sections = Object.fromEntries(page.sections.map((section) => [section.id, section.title]))

  return <DocArticle title={page.title} description={page.description}>
    <Callout type="note"><FeatureBadge kind="admin">{t("adminBadge")}</FeatureBadge> {t("adminNote")}</Callout>
    <DocSection id="configure" title={sections.configure}>
      <p>{t.rich("configureBody", { strong: (chunks) => <strong>{chunks}</strong> })}</p>
      <BulletList>
        <li>{t("chooseIcon")}</li>
        <li>{t.rich("chooseStyle", { strong: (chunks) => <strong>{chunks}</strong> })}</li>
        <li><FeatureBadge kind="pro">Pro</FeatureBadge> {t("branding")}</li>
      </BulletList>
    </DocSection>
    <DocSection id="origins" title={sections.origins}>
      <p>{t.rich("originsBody", { code: (chunks) => <code>{chunks}</code> })}</p>
      <Callout type="security">{t("originsSecurity")}</Callout>
    </DocSection>
    <DocSection id="token" title={sections.token}>
      <Steps>
        <Step title={t("openSettingsTitle")}>{t("openSettingsText")}</Step>
        <Step title={t("generateTitle")}>{t.rich("generateText", { code: (chunks) => <code>{chunks}</code> })}</Step>
        <Step title={t("saveTitle")}>{t.rich("saveText", { code: (chunks) => <code>{chunks}</code> })}</Step>
      </Steps>
      <Callout type="warning">{t("rotationWarning")}</Callout>
    </DocSection>
    <DocSection id="embed" title={sections.embed}>
      <p>{t("embedIntro")}</p>
      <CodeBlock language="html" code={embed} />
      <p>{t.rich("embedPlacement", { code: (chunks) => <code>{chunks}</code> })}</p>
    </DocSection>
    <DocSection id="security" title={sections.security}>
      <BulletList>
        <li>{t.rich("browserToken", { code: (chunks) => <code>{chunks}</code> })}</li>
        <li>{t.rich("neverApiToken", { code: (chunks) => <code>{chunks}</code> })}</li>
        <li>{t("keepInactive")}</li>
        <li>{t("revoke")}</li>
      </BulletList>
      <Callout type="security" title={t("evidenceTitle")}>{t.rich("evidenceText", { code: (chunks) => <code>{chunks}</code> })}</Callout>
      <RelatedLinks links={[{ href: "/documentation/documents#visibility", title: t("visibilityTitle"), description: t("visibilityDescription") }, { href: "/documentation/support", title: t("supportTitle"), description: t("supportDescription") }]} />
    </DocSection>
  </DocArticle>
}

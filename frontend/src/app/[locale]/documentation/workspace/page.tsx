import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"

import { BulletList, Callout, DocArticle, DocSection, FeatureBadge, ParameterTable } from "@/components/documentation/DocumentationComponents"
import type { AppLocale } from "@/i18n/routing"
import { documentationPage } from "@/lib/documentation"

type PageProps = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale } = await params
  const page = documentationPage("/documentation/workspace", locale as AppLocale)
  return { title: page.title, description: page.description }
}

export default async function WorkspaceDocumentationPage({ params }: PageProps) {
  const { locale } = await params
  const t = await getTranslations({ locale: locale as AppLocale, namespace: "DocumentationContent.workspace" })
  const page = documentationPage("/documentation/workspace", locale as AppLocale)
  const sections = Object.fromEntries(page.sections.map((section) => [section.id, section.title]))
  const features = [
    [t("apiTitle"), t("apiDetail")],
    [t("webhooksTitle"), t("webhooksDetail")],
    [t("analyticsTitle"), t("analyticsDetail")],
    [t("brandingTitle"), t("brandingDetail")],
  ]

  return <DocArticle title={page.title} description={page.description}>
    <Callout type="note"><FeatureBadge kind="admin">{t("adminBadge")}</FeatureBadge> {t("adminNote")}</Callout>
    <DocSection id="instructions" title={sections.instructions}>
      <p>{t("instructionsBody")}</p>
      <BulletList><li>{t("instructionsBlank")}</li><li>{t("instructionsChanges")}</li><li>{t("instructionsRestore")}</li><li>{t("instructionsBoundary")}</li></BulletList>
      <Callout type="tip">{t("instructionsTip")}</Callout>
    </DocSection>
    <DocSection id="quotas" title={sections.quotas}>
      <ParameterTable parameters={[
        { name: t("messagesName"), type: t("messagesType"), description: t("messagesDescription") },
        { name: t("documentsName"), type: t("liveTotalType"), description: t("documentsDescription") },
        { name: t("membersName"), type: t("liveTotalType"), description: t("membersDescription") },
        { name: t("storageName"), type: t("liveTotalType"), description: t("storageDescription") },
        { name: t("hiringName"), type: t("hiringType"), description: t("hiringDescription") },
      ]} />
      <p>{t("quotaBody")}</p>
    </DocSection>
    <DocSection id="billing" title={sections.billing}>
      <BulletList>
        <li>{t("billingTrial")}</li>
        <li>{t("billingIntervals")}</li>
        <li>{t.rich("billingRenewal", { code: (chunks) => <code>{chunks}</code> })}</li>
        <li>{t("billingGrace")}</li>
        <li>{t("billingFallback")}</li>
        <li>{t("billingDowngrade")}</li>
        <li>{t("billingSwitch")}</li>
      </BulletList>
      <Callout type="security">{t("billingSecurity")}</Callout>
    </DocSection>
    <DocSection id="features" title={sections.features}>
      <div className="grid gap-3 sm:grid-cols-2">{features.map(([name, detail]) => <div key={name} className="rounded-lg border p-4"><FeatureBadge kind="pro">Pro</FeatureBadge><h3 className="mt-2 font-semibold text-slate-950">{name}</h3><p className="text-sm text-slate-600">{detail}</p></div>)}</div>
      <p>{t("starterFeatures")}</p>
    </DocSection>
    <DocSection id="tokens" title={sections.tokens}>
      <p>{t.rich("tokensBody", { code: (chunks) => <code>{chunks}</code> })}</p>
      <BulletList><li>{t("tokensName")}</li><li>{t("tokensExpiry")}</li><li>{t("tokensRotate")}</li><li>{t("tokensManaged")}</li><li>{t("tokensDeleted")}</li></BulletList>
    </DocSection>
    <DocSection id="admin" title={sections.admin}>
      <p>{t("adminBody")}</p>
    </DocSection>
  </DocArticle>
}

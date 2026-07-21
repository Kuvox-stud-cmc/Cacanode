import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"

import { BulletList, Callout, DocArticle, DocSection, FeatureBadge, ParameterTable, Step, Steps } from "@/components/documentation/DocumentationComponents"
import type { AppLocale } from "@/i18n/routing"
import { documentationPage } from "@/lib/documentation"

type PageProps = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale } = await params
  const page = documentationPage("/documentation/analytics-team", locale as AppLocale)
  return { title: page.title, description: page.description }
}

export default async function AnalyticsTeamDocumentationPage({ params }: PageProps) {
  const { locale } = await params
  const t = await getTranslations({ locale: locale as AppLocale, namespace: "DocumentationContent.analyticsTeam" })
  const page = documentationPage("/documentation/analytics-team", locale as AppLocale)
  const sections = Object.fromEntries(page.sections.map((section) => [section.id, section.title]))

  return <DocArticle title={page.title} description={page.description}>
    <DocSection id="dashboard" title={sections.dashboard}>
      <p>{t("dashboardBody")}</p>
      <Callout type="note">{t("dashboardNote")}</Callout>
    </DocSection>
    <DocSection id="analytics" title={sections.analytics}>
      <p><FeatureBadge kind="pro">Pro</FeatureBadge> {t("analyticsBody")}</p>
      <p>{t("analyticsComparison")}</p>
    </DocSection>
    <DocSection id="date-ranges" title={sections["date-ranges"]}>
      <ParameterTable parameters={[
        { name: "CUSTOMER", type: t("scopeType"), description: t("customerDescription") },
        { name: "EMPLOYEE", type: t("scopeType"), description: t("employeeDescription") },
        { name: "ALL", type: t("scopeType"), description: t("allDescription") },
        { name: "7 / 30 / 90", type: t("daysType"), description: t("daysDescription") },
      ]} />
    </DocSection>
    <DocSection id="roles" title={sections.roles}>
      <div className="grid gap-4 sm:grid-cols-2"><div className="rounded-lg border p-4"><FeatureBadge>USER</FeatureBadge><p className="mt-3 text-sm leading-6">{t("userRole")}</p></div><div className="rounded-lg border p-4"><FeatureBadge kind="admin">TENANT_ADMIN</FeatureBadge><p className="mt-3 text-sm leading-6">{t("adminRole")}</p></div></div>
    </DocSection>
    <DocSection id="invitations" title={sections.invitations}>
      <Steps>
        <Step title={t("inviteTitle")}>{t.rich("inviteText", { code: (chunks) => <code>{chunks}</code> })}</Step>
        <Step title={t("followUpTitle")}>{t("followUpText")}</Step>
        <Step title={t("manageTitle")}>{t.rich("manageText", { code: (chunks) => <code>{chunks}</code> })}</Step>
        <Step title={t("reactivateTitle")}>{t("reactivateText")}</Step>
      </Steps>
      <Callout type="security">{t("deactivationSecurity")}</Callout>
    </DocSection>
    <DocSection id="protections" title={sections.protections}>
      <BulletList><li>{t("selfRole")}</li><li>{t("selfDeactivate")}</li><li>{t("finalAdmin")}</li><li>{t("secondAdmin")}</li></BulletList>
    </DocSection>
  </DocArticle>
}

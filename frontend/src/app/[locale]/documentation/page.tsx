import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"
import { Code2, FileUp, Settings, Users } from "lucide-react"

import { Callout, DocArticle, DocSection, FeatureBadge, RelatedLinks } from "@/components/documentation/DocumentationComponents"
import { Link } from "@/i18n/navigation"
import type { AppLocale } from "@/i18n/routing"
import { documentationPage } from "@/lib/documentation"

type PageProps = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale } = await params
  const page = documentationPage("/documentation", locale as AppLocale)
  return { title: page.title, description: page.description }
}

export default async function DocumentationOverviewPage({ params }: PageProps) {
  const { locale } = await params
  const t = await getTranslations({ locale: locale as AppLocale, namespace: "DocumentationContent.overview" })
  const page = documentationPage("/documentation", locale as AppLocale)
  const sections = Object.fromEntries(page.sections.map((section) => [section.id, section.title]))
  const quickstarts = [
    { icon: FileUp, title: t("quickstarts.dashboardTitle"), text: t("quickstarts.dashboardText"), href: "/documentation/getting-started" },
    { icon: Settings, title: t("quickstarts.adminTitle"), text: t("quickstarts.adminText"), href: "/documentation/workspace" },
    { icon: Code2, title: t("quickstarts.installerTitle"), text: t("quickstarts.installerText"), href: "/documentation/widget" },
    { icon: Users, title: t("quickstarts.apiTitle"), text: t("quickstarts.apiText"), href: "/documentation/api" },
  ]
  const concepts = [
    [t("workspaceTitle"), t("workspaceText")],
    [t("knowledgeBaseTitle"), t("knowledgeBaseText")],
    [t("chatbotTitle"), t("chatbotText")],
    [t("conversationTitle"), t("conversationText")],
  ]

  return <DocArticle title={page.title} description={page.description}>
    <DocSection id="core-concepts" title={sections["core-concepts"]}>
      <div className="grid gap-4 sm:grid-cols-2">
        {concepts.map(([title, text]) => <div key={title} className="rounded-lg border border-slate-200 p-4"><h3 className="font-semibold text-slate-950">{title}</h3><p className="mt-1 text-sm leading-6 text-slate-600">{text}</p></div>)}
      </div>
      <Callout type="note" title={t("evidenceTitle")}>{t.rich("evidenceText", { code: (chunks) => <code>{chunks}</code> })}</Callout>
    </DocSection>
    <DocSection id="quickstarts" title={sections.quickstarts}>
      <div className="grid gap-4 sm:grid-cols-2">
        {quickstarts.map(({ icon: Icon, title, text, href }) => <Link key={title} href={href} className="rounded-xl border border-slate-200 p-5 hover:border-indigo-300 hover:bg-indigo-50/30"><Icon className="size-5 text-indigo-600" /><h3 className="mt-3 font-semibold text-slate-950">{title}</h3><p className="mt-1 text-sm leading-6 text-slate-600">{text}</p></Link>)}
      </div>
    </DocSection>
    <DocSection id="integration-paths" title={sections["integration-paths"]}>
      <div className="space-y-4">
        <p><FeatureBadge>{t("employeeBadge")}</FeatureBadge> {t("employeePath")}</p>
        <p><FeatureBadge>{t("customerBadge")}</FeatureBadge> {t("customerPath")}</p>
        <p><FeatureBadge kind="pro">Pro</FeatureBadge> {t("proPath")}</p>
      </div>
      <RelatedLinks links={[{ href: "/documentation/documents", title: t("documentsTitle"), description: t("documentsDescription") }, { href: "/documentation/widget", title: t("widgetTitle"), description: t("widgetDescription") }]} />
    </DocSection>
  </DocArticle>
}

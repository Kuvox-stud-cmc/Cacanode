import Link from "next/link"
import { Code2, FileUp, Settings, Users } from "lucide-react"
import { Callout, DocArticle, DocSection, FeatureBadge, RelatedLinks } from "@/components/documentation/DocumentationComponents"

const quickstarts = [
  { icon: FileUp, title: "Dashboard user", text: "Upload sources, test answers in the playground, and inspect citations.", href: "/documentation/getting-started" },
  { icon: Settings, title: "Tenant administrator", text: "Configure customer answers, widget settings, tokens, billing, and team access.", href: "/documentation/workspace" },
  { icon: Code2, title: "Widget installer", text: "Generate a managed widget token, restrict origins, and add the embed script.", href: "/documentation/widget" },
  { icon: Users, title: "API integrator", text: "Create a scoped token and build a server-side conversation workflow.", href: "/documentation/api" },
]

export default function DocumentationOverviewPage() {
  return <DocArticle title="Build support experiences grounded in your content" description="CacaNode turns workspace documents into cited answers for employees, website visitors, and custom integrations. These guides cover the complete product and integration workflow.">
    <DocSection id="core-concepts" title="Core concepts">
      <div className="grid gap-4 sm:grid-cols-2">
        {[
          ["Workspace", "The tenant boundary for documents, people, billing, integrations, and support data."],
          ["Knowledge base", "The indexed collection used to retrieve evidence for an answer."],
          ["Chatbot", "The answer experience connected to the workspace knowledge base."],
          ["Conversation", "A sequence of messages from the employee playground, widget, or Custom API."],
        ].map(([title, text]) => <div key={title} className="rounded-lg border border-slate-200 p-4"><h3 className="font-semibold text-slate-950">{title}</h3><p className="mt-1 text-sm leading-6 text-slate-600">{text}</p></div>)}
      </div>
      <Callout type="note" title="Answers are evidence-based">CacaNode citations point back to indexed document units. Customer channels only expose evidence from completed documents marked <code>CUSTOMER_AND_EMPLOYEE</code>.</Callout>
    </DocSection>
    <DocSection id="quickstarts" title="Quickstarts by role">
      <div className="grid gap-4 sm:grid-cols-2">
        {quickstarts.map(({ icon: Icon, title, text, href }) => <Link key={title} href={href} className="rounded-xl border border-slate-200 p-5 hover:border-indigo-300 hover:bg-indigo-50/30"><Icon className="size-5 text-indigo-600" /><h3 className="mt-3 font-semibold text-slate-950">{title}</h3><p className="mt-1 text-sm leading-6 text-slate-600">{text}</p></Link>)}
      </div>
    </DocSection>
    <DocSection id="integration-paths" title="Choose an integration path">
      <div className="space-y-4">
        <p><FeatureBadge>Employee</FeatureBadge> Use the playground for private, authenticated workspace questions.</p>
        <p><FeatureBadge>Customer</FeatureBadge> Install the hosted widget when you want CacaNode to provide the browser UI and conversation flow.</p>
        <p><FeatureBadge kind="pro">Pro</FeatureBadge> Use the Custom Chat API when your server or application owns the UI and orchestration.</p>
      </div>
      <RelatedLinks links={[{ href: "/documentation/documents", title: "Prepare documents", description: "Understand formats, visibility, indexing, and citations." }, { href: "/documentation/widget", title: "Install the widget", description: "Configure a secure customer-facing chat experience." }]} />
    </DocSection>
  </DocArticle>
}

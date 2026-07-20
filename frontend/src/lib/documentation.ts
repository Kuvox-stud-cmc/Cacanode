export type DocumentationSection = {
  id: string
  title: string
  keywords?: string[]
}

export type DocumentationPage = {
  href: string
  title: string
  description: string
  group: "Start here" | "Product guides" | "Integrations" | "Administration"
  keywords: string[]
  sections: DocumentationSection[]
}

export const documentationPages: DocumentationPage[] = [
  {
    href: "/documentation",
    title: "Documentation overview",
    description: "Learn the CacaNode workflow and choose the right path for your role.",
    group: "Start here",
    keywords: ["overview", "concepts", "quickstart", "roles"],
    sections: [
      { id: "core-concepts", title: "Core concepts", keywords: ["workspace", "knowledge base", "chatbot"] },
      { id: "quickstarts", title: "Quickstarts by role", keywords: ["administrator", "installer", "developer"] },
      { id: "integration-paths", title: "Choose an integration path", keywords: ["playground", "widget", "api"] },
    ],
  },
  {
    href: "/documentation/getting-started",
    title: "Getting started",
    description: "Upload your first source, wait for indexing, and test an answer.",
    group: "Start here",
    keywords: ["upload", "indexing", "first answer", "setup"],
    sections: [
      { id: "upload", title: "1. Upload a document" },
      { id: "index", title: "2. Wait for indexing" },
      { id: "test", title: "3. Test the playground" },
      { id: "next-step", title: "4. Choose what to build next" },
    ],
  },
  {
    href: "/documentation/documents",
    title: "Documents",
    description: "Manage source files, indexing, visibility, search, citations, and deletion.",
    group: "Product guides",
    keywords: ["pdf", "docx", "xlsx", "csv", "visibility", "citations"],
    sections: [
      { id: "supported-files", title: "Supported files and limits" },
      { id: "indexing", title: "Indexing statuses" },
      { id: "visibility", title: "Document visibility" },
      { id: "viewer-citations", title: "Viewer and citations" },
      { id: "manage", title: "Search, filter, and delete" },
    ],
  },
  {
    href: "/documentation/playground",
    title: "Chat playground",
    description: "Test employee answers, inspect sources, and manage conversation history.",
    group: "Product guides",
    keywords: ["chat", "history", "sources", "conversations", "citations"],
    sections: [
      { id: "ask", title: "Ask questions" },
      { id: "sources", title: "Review sources and citations" },
      { id: "history", title: "Search conversation history" },
      { id: "manage", title: "Manage conversations" },
    ],
  },
  {
    href: "/documentation/widget",
    title: "Website widget",
    description: "Brand, secure, and install the hosted customer chat widget.",
    group: "Integrations",
    keywords: ["embed", "branding", "origins", "widget:chat", "token", "icon"],
    sections: [
      { id: "configure", title: "Configure the widget" },
      { id: "origins", title: "Restrict allowed origins" },
      { id: "token", title: "Generate the managed token" },
      { id: "embed", title: "Add the embed script" },
      { id: "security", title: "Security and evidence links" },
    ],
  },
  {
    href: "/documentation/api",
    title: "Custom Chat API",
    description: "Build server-side chat integrations with scoped Bearer tokens.",
    group: "Integrations",
    keywords: ["rest", "bearer", "api:chat", "sessions", "messages", "tickets"],
    sections: [
      { id: "authentication", title: "Authentication" },
      { id: "create-session", title: "Create a session" },
      { id: "send-message", title: "Send a message" },
      { id: "history-close", title: "Read history and close" },
      { id: "idempotency", title: "Idempotency and request tracing" },
      { id: "tickets", title: "Create a support ticket" },
      { id: "citations", title: "Citations and public evidence" },
    ],
  },
  {
    href: "/documentation/support",
    title: "Conversations and tickets",
    description: "Review customer conversations and manage the support ticket lifecycle.",
    group: "Product guides",
    keywords: ["support", "customer", "ticket", "assignee", "priority", "notes"],
    sections: [
      { id: "conversations", title: "Customer conversations" },
      { id: "channels", title: "Channels and closing" },
      { id: "tickets", title: "Ticket fields" },
      { id: "workflow", title: "Ticket lifecycle" },
      { id: "filters", title: "Filters, assignees, and notes" },
    ],
  },
  {
    href: "/documentation/webhooks",
    title: "Webhooks",
    description: "Receive signed conversation and ticket lifecycle events.",
    group: "Integrations",
    keywords: ["hmac", "signature", "retry", "events", "secret"],
    sections: [
      { id: "requirements", title: "Requirements and events" },
      { id: "payload", title: "Payload envelope" },
      { id: "verify", title: "Verify signatures" },
      { id: "delivery", title: "Delivery and retries" },
      { id: "testing-rotation", title: "Testing and secret rotation" },
    ],
  },
  {
    href: "/documentation/analytics-team",
    title: "Analytics and team",
    description: "Understand workspace metrics and safely manage team access.",
    group: "Administration",
    keywords: ["metrics", "pro", "roles", "invitations", "deactivate"],
    sections: [
      { id: "dashboard", title: "Dashboard metrics" },
      { id: "analytics", title: "Pro analytics" },
      { id: "date-ranges", title: "Scopes and date ranges" },
      { id: "roles", title: "Team roles" },
      { id: "invitations", title: "Invitations and deactivation" },
      { id: "protections", title: "Administrator protections" },
    ],
  },
  {
    href: "/documentation/workspace",
    title: "Workspace settings",
    description: "Configure answer instructions, plans, quotas, billing, and integrations.",
    group: "Administration",
    keywords: ["prompt", "quota", "billing", "plans", "tokens", "admin"],
    sections: [
      { id: "instructions", title: "Customer answer instructions" },
      { id: "quotas", title: "Quotas and usage" },
      { id: "billing", title: "Billing lifecycle" },
      { id: "features", title: "Plan-gated features" },
      { id: "tokens", title: "Integration tokens" },
      { id: "admin", title: "Administrator-only settings" },
    ],
  },
]

export const documentationGroups = ["Start here", "Product guides", "Integrations", "Administration"] as const

export function documentationPage(pathname: string) {
  const normalized = pathname !== "/" ? pathname.replace(/\/$/, "") : pathname
  return documentationPages.find((page) => page.href === normalized) ?? documentationPages[0]
}


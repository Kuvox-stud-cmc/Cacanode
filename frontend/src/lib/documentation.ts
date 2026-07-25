import type { AppLocale } from "@/i18n/routing"

export type DocumentationGroupId = "start" | "product" | "integrations" | "administration"

export type DocumentationSection = {
  id: string
  title: string
  keywords: string[]
}

export type DocumentationPage = {
  href: string
  title: string
  description: string
  group: DocumentationGroupId
  keywords: string[]
  sections: DocumentationSection[]
}

type LocalizedPage = Omit<DocumentationPage, "href" | "group" | "sections"> & {
  sections: Record<string, { title: string; keywords?: string[] }>
}

const structure = [
  { href: "/documentation", group: "start", sections: ["core-concepts", "quickstarts", "integration-paths"] },
  { href: "/documentation/getting-started", group: "start", sections: ["upload", "index", "test", "next-step"] },
  { href: "/documentation/documents", group: "product", sections: ["supported-files", "indexing", "visibility", "viewer-citations", "manage"] },
  { href: "/documentation/playground", group: "product", sections: ["ask", "sources", "history", "manage"] },
  { href: "/documentation/widget", group: "integrations", sections: ["configure", "origins", "token", "embed", "security"] },
  { href: "/documentation/api", group: "integrations", sections: ["authentication", "create-session", "send-message", "history-close", "idempotency", "tickets", "citations"] },
  { href: "/documentation/support", group: "product", sections: ["conversations", "channels", "tickets", "workflow", "filters"] },
  { href: "/documentation/recruitment", group: "product", sections: ["jobs", "applications", "roles", "scheduling", "quotas"] },
  { href: "/documentation/ai-interviews", group: "product", sections: ["setup", "templates", "personalization", "consent-recording", "results-privacy", "troubleshooting"] },
  { href: "/documentation/webhooks", group: "integrations", sections: ["requirements", "payload", "verify", "delivery", "testing-rotation"] },
  { href: "/documentation/analytics-team", group: "administration", sections: ["dashboard", "analytics", "date-ranges", "roles", "invitations", "protections"] },
  { href: "/documentation/workspace", group: "administration", sections: ["instructions", "quotas", "billing", "features", "tokens", "admin"] },
] as const

export const documentationGroupOrder: DocumentationGroupId[] = ["start", "product", "integrations", "administration"]

const groupTitles: Record<AppLocale, Record<DocumentationGroupId, string>> = {
  en: { start: "Start here", product: "Product guides", integrations: "Integrations", administration: "Administration" },
  vi: { start: "Bắt đầu", product: "Hướng dẫn sản phẩm", integrations: "Tích hợp", administration: "Quản trị" },
}

const content: Record<AppLocale, Record<string, LocalizedPage>> = {
  en: {
    "/documentation": {
      title: "Documentation overview",
      description: "Learn the CacaNode workflow and choose the right path for your role.",
      keywords: ["overview", "concepts", "quickstart", "roles"],
      sections: {
        "core-concepts": { title: "Core concepts", keywords: ["workspace", "knowledge base", "chatbot"] },
        quickstarts: { title: "Quickstarts by role", keywords: ["administrator", "installer", "developer"] },
        "integration-paths": { title: "Choose an integration path", keywords: ["playground", "widget", "api"] },
      },
    },
    "/documentation/getting-started": {
      title: "Getting started",
      description: "Upload your first source, wait for indexing, and test an answer.",
      keywords: ["upload", "indexing", "first answer", "setup"],
      sections: { upload: { title: "1. Upload a document" }, index: { title: "2. Wait for indexing" }, test: { title: "3. Test the playground" }, "next-step": { title: "4. Choose what to build next" } },
    },
    "/documentation/documents": {
      title: "Documents",
      description: "Manage source files, indexing, visibility, search, citations, and deletion.",
      keywords: ["pdf", "docx", "xlsx", "csv", "visibility", "citations"],
      sections: { "supported-files": { title: "Supported files and limits" }, indexing: { title: "Indexing statuses" }, visibility: { title: "Document visibility" }, "viewer-citations": { title: "Viewer and citations" }, manage: { title: "Search, filter, and delete" } },
    },
    "/documentation/playground": {
      title: "Chat playground",
      description: "Test employee answers, inspect sources, and manage conversation history.",
      keywords: ["chat", "history", "sources", "conversations", "citations"],
      sections: { ask: { title: "Ask questions" }, sources: { title: "Review sources and citations" }, history: { title: "Search conversation history" }, manage: { title: "Manage conversations" } },
    },
    "/documentation/widget": {
      title: "Website widget",
      description: "Brand, secure, and install the hosted customer chat widget.",
      keywords: ["embed", "branding", "origins", "widget:chat", "token", "icon"],
      sections: { configure: { title: "Configure the widget" }, origins: { title: "Restrict allowed origins" }, token: { title: "Generate the managed token" }, embed: { title: "Add the embed script" }, security: { title: "Security and evidence links" } },
    },
    "/documentation/api": {
      title: "Custom Chat API",
      description: "Build server-side chat integrations with scoped Bearer tokens.",
      keywords: ["rest", "bearer", "api:chat", "sessions", "messages", "tickets"],
      sections: { authentication: { title: "Authentication" }, "create-session": { title: "Create a session" }, "send-message": { title: "Send a message" }, "history-close": { title: "Read history and close" }, idempotency: { title: "Idempotency and request tracing" }, tickets: { title: "Create a support ticket" }, citations: { title: "Citations and public evidence" } },
    },
    "/documentation/support": {
      title: "Conversations and tickets",
      description: "Review customer conversations and manage the support ticket lifecycle.",
      keywords: ["support", "customer", "ticket", "assignee", "priority", "notes"],
      sections: { conversations: { title: "Customer conversations" }, channels: { title: "Channels and closing" }, tickets: { title: "Ticket fields" }, workflow: { title: "Ticket lifecycle" }, filters: { title: "Filters, assignees, and notes" } },
    },
    "/documentation/webhooks": {
      title: "Webhooks",
      description: "Receive signed conversation and ticket lifecycle events.",
      keywords: ["hmac", "signature", "retry", "events", "secret"],
      sections: { requirements: { title: "Requirements and events" }, payload: { title: "Payload envelope" }, verify: { title: "Verify signatures" }, delivery: { title: "Delivery and retries" }, "testing-rotation": { title: "Testing and secret rotation" } },
    },
    "/documentation/recruitment": { title: "Recruitment", description: "Manage jobs, applications, recruiter roles, scheduling, and hiring quotas.", keywords: ["jobs","applications","candidates","schedule","quota"], sections: { jobs:{title:"Jobs and publication"},applications:{title:"Applications and candidate evidence"},roles:{title:"Recruiter roles"},scheduling:{title:"Scheduling"},quotas:{title:"Hiring quotas"} } },
    "/documentation/ai-interviews": { title: "AI interview setup", description: "Configure bilingual interview templates, consent, recording, advisory results, privacy, and retention.", keywords:["interview","template","Vietnamese","English","recording","privacy"], sections:{setup:{title:"Set up AI interviews"},templates:{title:"Templates and language sections"},personalization:{title:"CV personalization"},"consent-recording":{title:"Consent and recording"},"results-privacy":{title:"Advisory results, privacy, and retention"},troubleshooting:{title:"Troubleshooting"}} },
    "/documentation/analytics-team": {
      title: "Analytics and team",
      description: "Understand workspace metrics and safely manage team access.",
      keywords: ["metrics", "pro", "roles", "invitations", "deactivate"],
      sections: { dashboard: { title: "Dashboard metrics" }, analytics: { title: "Pro analytics" }, "date-ranges": { title: "Scopes and date ranges" }, roles: { title: "Team roles" }, invitations: { title: "Invitations and deactivation" }, protections: { title: "Administrator protections" } },
    },
    "/documentation/workspace": {
      title: "Workspace settings",
      description: "Configure answer instructions, plans, quotas, billing, and integrations.",
      keywords: ["prompt", "quota", "billing", "plans", "tokens", "admin"],
      sections: { instructions: { title: "Customer answer instructions" }, quotas: { title: "Quotas and usage" }, billing: { title: "Billing lifecycle" }, features: { title: "Plan-gated features" }, tokens: { title: "Integration tokens" }, admin: { title: "Administrator-only settings" } },
    },
  },
  vi: {
    "/documentation": {
      title: "Tổng quan tài liệu",
      description: "Tìm hiểu quy trình CacaNode và chọn lộ trình phù hợp với vai trò của bạn.",
      keywords: ["tong quan", "khai niem", "bat dau nhanh", "vai tro"],
      sections: {
        "core-concepts": { title: "Các khái niệm cốt lõi", keywords: ["không gian làm việc", "kho tri thức", "chatbot"] },
        quickstarts: { title: "Bắt đầu nhanh theo vai trò", keywords: ["quản trị viên", "người cài đặt", "lập trình viên"] },
        "integration-paths": { title: "Chọn cách tích hợp", keywords: ["bảng trò chuyện", "tiện ích", "api"] },
      },
    },
    "/documentation/getting-started": {
      title: "Bắt đầu",
      description: "Tải nguồn dữ liệu đầu tiên lên, chờ lập chỉ mục và thử một câu trả lời.",
      keywords: ["tải lên", "lập chỉ mục", "câu trả lời đầu tiên", "thiết lập"],
      sections: { upload: { title: "1. Tải tài liệu lên" }, index: { title: "2. Chờ lập chỉ mục" }, test: { title: "3. Thử khu trò chuyện" }, "next-step": { title: "4. Chọn bước tiếp theo" } },
    },
    "/documentation/documents": {
      title: "Tài liệu",
      description: "Quản lý tệp nguồn, lập chỉ mục, phạm vi truy cập, tìm kiếm, trích dẫn và xóa.",
      keywords: ["pdf", "docx", "xlsx", "csv", "quyền xem", "trích dẫn"],
      sections: { "supported-files": { title: "Tệp được hỗ trợ và giới hạn" }, indexing: { title: "Trạng thái lập chỉ mục" }, visibility: { title: "Phạm vi truy cập tài liệu" }, "viewer-citations": { title: "Trình xem và trích dẫn" }, manage: { title: "Tìm kiếm, lọc và xóa" } },
    },
    "/documentation/playground": {
      title: "Bảng trò chuyện",
      description: "Thử câu trả lời cho nhân viên, xem nguồn và quản lý lịch sử hội thoại.",
      keywords: ["trò chuyện", "lịch sử", "nguồn", "cuộc trò chuyện", "trích dẫn"],
      sections: { ask: { title: "Đặt câu hỏi" }, sources: { title: "Xem nguồn và trích dẫn" }, history: { title: "Tìm lịch sử hội thoại" }, manage: { title: "Quản lý cuộc trò chuyện" } },
    },
    "/documentation/widget": {
      title: "Tiện ích trang web",
      description: "Tùy chỉnh thương hiệu, bảo mật và cài đặt tiện ích trò chuyện dành cho khách hàng.",
      keywords: ["nhúng", "thương hiệu", "nguồn gốc", "widget:chat", "token", "biểu tượng"],
      sections: { configure: { title: "Cấu hình tiện ích" }, origins: { title: "Giới hạn nguồn gốc được phép" }, token: { title: "Tạo token được quản lý" }, embed: { title: "Thêm mã nhúng" }, security: { title: "Bảo mật và liên kết bằng chứng" } },
    },
    "/documentation/api": {
      title: "API trò chuyện tùy chỉnh",
      description: "Xây dựng tích hợp trò chuyện phía máy chủ bằng Bearer token có phạm vi.",
      keywords: ["rest", "bearer", "api:chat", "phiên", "tin nhắn", "phiếu hỗ trợ"],
      sections: { authentication: { title: "Xác thực" }, "create-session": { title: "Tạo phiên" }, "send-message": { title: "Gửi tin nhắn" }, "history-close": { title: "Đọc lịch sử và đóng phiên" }, idempotency: { title: "Tính lũy đẳng và truy vết yêu cầu" }, tickets: { title: "Tạo phiếu hỗ trợ" }, citations: { title: "Trích dẫn và bằng chứng công khai" } },
    },
    "/documentation/support": {
      title: "Cuộc trò chuyện và phiếu hỗ trợ",
      description: "Xem các cuộc trò chuyện với khách hàng và quản lý vòng đời phiếu hỗ trợ.",
      keywords: ["hỗ trợ", "khách hàng", "phiếu", "người phụ trách", "ưu tiên", "ghi chú"],
      sections: { conversations: { title: "Cuộc trò chuyện với khách hàng" }, channels: { title: "Kênh và đóng phiên" }, tickets: { title: "Trường của phiếu hỗ trợ" }, workflow: { title: "Vòng đời phiếu hỗ trợ" }, filters: { title: "Bộ lọc, người phụ trách và ghi chú" } },
    },
    "/documentation/webhooks": {
      title: "Webhook",
      description: "Nhận sự kiện vòng đời cuộc trò chuyện và phiếu hỗ trợ có chữ ký.",
      keywords: ["hmac", "chữ ký", "thử lại", "sự kiện", "bí mật"],
      sections: { requirements: { title: "Yêu cầu và sự kiện" }, payload: { title: "Cấu trúc payload" }, verify: { title: "Xác minh chữ ký" }, delivery: { title: "Phân phối và thử lại" }, "testing-rotation": { title: "Kiểm thử và xoay vòng bí mật" } },
    },
    "/documentation/recruitment": { title: "Tuyển dụng", description: "Quản lý tin tuyển dụng, hồ sơ, vai trò, lịch phỏng vấn và hạn mức.", keywords:["tuyển dụng","hồ sơ","ứng viên","lịch","hạn mức"], sections:{jobs:{title:"Tin tuyển dụng và xuất bản"},applications:{title:"Hồ sơ và bằng chứng ứng viên"},roles:{title:"Vai trò nhà tuyển dụng"},scheduling:{title:"Lên lịch"},quotas:{title:"Hạn mức tuyển dụng"}} },
    "/documentation/ai-interviews": { title: "Thiết lập phỏng vấn AI", description: "Cấu hình mẫu song ngữ, đồng ý, ghi âm, kết quả tham khảo, quyền riêng tư và lưu giữ.", keywords:["phỏng vấn","mẫu","tiếng Việt","tiếng Anh","ghi âm","quyền riêng tư"], sections:{setup:{title:"Thiết lập phỏng vấn AI"},templates:{title:"Mẫu và phần ngôn ngữ"},personalization:{title:"Cá nhân hóa từ CV"},"consent-recording":{title:"Đồng ý và ghi âm"},"results-privacy":{title:"Kết quả tham khảo, quyền riêng tư và lưu giữ"},troubleshooting:{title:"Khắc phục sự cố"}} },
    "/documentation/analytics-team": {
      title: "Phân tích và đội ngũ",
      description: "Hiểu các chỉ số không gian làm việc và quản lý quyền truy cập an toàn.",
      keywords: ["chỉ số", "pro", "vai trò", "lời mời", "vô hiệu hóa"],
      sections: { dashboard: { title: "Chỉ số tổng quan" }, analytics: { title: "Phân tích Pro" }, "date-ranges": { title: "Phạm vi và khoảng ngày" }, roles: { title: "Vai trò trong đội ngũ" }, invitations: { title: "Lời mời và vô hiệu hóa" }, protections: { title: "Biện pháp bảo vệ quản trị viên" } },
    },
    "/documentation/workspace": {
      title: "Cài đặt không gian làm việc",
      description: "Cấu hình hướng dẫn trả lời, gói dịch vụ, hạn mức, thanh toán và tích hợp.",
      keywords: ["chỉ dẫn", "hạn mức", "thanh toán", "gói", "token", "quản trị"],
      sections: { instructions: { title: "Hướng dẫn trả lời khách hàng" }, quotas: { title: "Hạn mức và mức sử dụng" }, billing: { title: "Vòng đời thanh toán" }, features: { title: "Tính năng theo gói" }, tokens: { title: "Token tích hợp" }, admin: { title: "Cài đặt chỉ dành cho quản trị viên" } },
    },
  },
}

export function getDocumentationGroups(locale: AppLocale) {
  return documentationGroupOrder.map((id) => ({ id, title: groupTitles[locale][id] }))
}

export function getDocumentationPages(locale: AppLocale): DocumentationPage[] {
  return structure.map((page) => {
    const localized = content[locale][page.href]
    return {
      href: page.href,
      group: page.group,
      title: localized.title,
      description: localized.description,
      keywords: localized.keywords,
      sections: page.sections.map((id) => ({
        id,
        title: localized.sections[id].title,
        keywords: localized.sections[id].keywords ?? [],
      })),
    }
  })
}

export function documentationPage(pathname: string, locale: AppLocale = "en") {
  const normalized = pathname !== "/" ? pathname.replace(/\/$/, "") : pathname
  const pages = getDocumentationPages(locale)
  return pages.find((page) => page.href === normalized) ?? pages[0]
}

export function normalizeDocumentationSearch(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLocaleLowerCase()
}

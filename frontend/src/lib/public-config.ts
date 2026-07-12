export const publicConfig = {
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL,
  aiApiBaseUrl: process.env.NEXT_PUBLIC_AI_API_BASE_URL,
  demoChatbotId:
    process.env.NEXT_PUBLIC_DEMO_CHATBOT_ID ??
    "00000000-0000-0000-0000-000000000005",
  demoKnowledgeBaseId:
    process.env.NEXT_PUBLIC_DEMO_KNOWLEDGE_BASE_ID ??
    "00000000-0000-0000-0000-000000000004",
  widgetUrl: process.env.NEXT_PUBLIC_WIDGET_URL,
  defaultLocale: process.env.NEXT_PUBLIC_DEFAULT_LOCALE ?? "vi-VN",
} as const;

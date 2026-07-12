export const publicConfig = {
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL,
  aiApiBaseUrl: process.env.NEXT_PUBLIC_AI_API_BASE_URL,
  widgetUrl: process.env.NEXT_PUBLIC_WIDGET_URL,
  defaultLocale: process.env.NEXT_PUBLIC_DEFAULT_LOCALE ?? "vi-VN",
} as const;

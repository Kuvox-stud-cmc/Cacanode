export const publicConfig = {
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL,
  widgetUrl: process.env.NEXT_PUBLIC_WIDGET_URL,
  defaultLocale: process.env.NEXT_PUBLIC_DEFAULT_LOCALE ?? "vi-VN",
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000",
  turnstileSiteKey: process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? "",
  recruitmentEnabled: process.env.NEXT_PUBLIC_RECRUITMENT_ENABLED === "true",
  platformAdministrationEnabled: process.env.NEXT_PUBLIC_PLATFORM_ADMINISTRATION_ENABLED === "true",
} as const;

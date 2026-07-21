"use client";

import { useEffect } from "react";
import { useLocale, useTranslations } from "next-intl";
import { usePathname } from "@/i18n/navigation";
import type { AppLocale } from "@/i18n/routing";
import { documentationPage } from "@/lib/documentation";

const PAGE_TITLE_KEYS = {
  "/": "chat",
  "/pricing": "pricing",
  "/login": "login",
  "/register": "register",
  "/check-email": "checkEmail",
  "/check-login-email": "checkLoginEmail",
  "/verify-email": "verifyEmail",
  "/verify-login": "verifyLogin",
  "/accept-invitation": "acceptInvitation",
  "/dashboard": "dashboard",
  "/documents": "documents",
  "/conversations": "conversations",
  "/tickets": "tickets",
  "/analytics": "analytics",
  "/users": "users",
  "/settings": "settings",
  "/widget/preview": "widgetPreview",
} as const;

export function PageTitle() {
  const pathname = usePathname();
  const locale = useLocale() as AppLocale;
  const t = useTranslations("PageTitles");

  useEffect(() => {
    const normalizedPath = pathname !== "/" ? pathname.replace(/\/$/, "") : pathname;
    let pageTitle: string | undefined;
    if (normalizedPath.startsWith("/documents/")) pageTitle = t("documentDetails");
    else if (normalizedPath === "/documentation" || normalizedPath.startsWith("/documentation/")) pageTitle = documentationPage(normalizedPath, locale).title;
    else {
      const key = PAGE_TITLE_KEYS[normalizedPath as keyof typeof PAGE_TITLE_KEYS];
      if (key) pageTitle = t(key);
    }
    document.title = pageTitle ? `${pageTitle} | CacaNode` : "CacaNode";
  }, [locale, pathname, t]);

  return null;
}

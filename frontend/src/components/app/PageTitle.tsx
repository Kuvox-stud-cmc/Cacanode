"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";

const PAGE_TITLES: Record<string, string> = {
  "/": "Chat",
  "/pricing": "Plans & Pricing",
  "/login": "Log In",
  "/register": "Create Account",
  "/check-email": "Check Your Email",
  "/check-login-email": "Check Your Login Email",
  "/verify-email": "Verify Email",
  "/verify-login": "Verify Login",
  "/accept-invitation": "Accept Invitation",
  "/dashboard": "Dashboard",
  "/documents": "Documents",
  "/conversations": "Conversations",
  "/tickets": "Support Tickets",
  "/analytics": "Analytics",
  "/users": "Team Members",
  "/settings": "Settings",
  "/widget/preview": "Widget Preview",
};

export function titleForPath(pathname: string): string {
  const normalizedPath = pathname !== "/" ? pathname.replace(/\/$/, "") : pathname;
  const pageTitle = normalizedPath.startsWith("/documents/")
    ? "Document Details"
    : PAGE_TITLES[normalizedPath];

  return pageTitle ? `${pageTitle} | CacaNode` : "CacaNode";
}

export function PageTitle() {
  const pathname = usePathname();

  useEffect(() => {
    document.title = titleForPath(pathname);
  }, [pathname]);

  return null;
}

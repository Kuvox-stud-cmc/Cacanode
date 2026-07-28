"use client"

import { useEffect, useState, type ReactNode } from "react"
import Image from "next/image"
import { useTranslations } from "next-intl"
import { Loader2, LogOut, Menu, X } from "lucide-react"
import { useAuthStore } from "@/components/providers/StoreProvider"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { logoutApi } from "@/lib/auth-api"
import { getBillingAccount, type BillingAccount } from "@/lib/billing-api"
import { useApiClient } from "@/hooks/useApiClient"
import { appNavigation } from "@/components/app/navigation"
import { PlanStatusBadge } from "@/components/billing/PlanStatusBadge"
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher"
import { Link, usePathname, useRouter } from "@/i18n/navigation"
import { withNext } from "@/lib/auth-redirect"
import { cn } from "@/lib/utils"
import { publicConfig } from "@/lib/public-config"
import { getRecruitmentCapabilities } from "@/lib/recruitment-admin-api"

function initialsFrom(fullName: string | undefined): string {
  if (!fullName?.trim()) return "?"
  const parts = fullName.trim().split(/\s+/)
  if (parts.length >= 2) {
    return `${parts[0]![0]}${parts[parts.length - 1]![0]}`.toUpperCase()
  }
  return fullName.slice(0, 2).toUpperCase()
}

type AppShellProps = {
  children: ReactNode
  contentClassName?: string
  mobileNavContent?: ReactNode
}

export function AppShell({ children, contentClassName, mobileNavContent }: AppShellProps) {
  const t = useTranslations("Navigation")
  const pathname = usePathname()
  const router = useRouter()
  const user = useAuthStore((state) => state.user)
  const setPlan = useAuthStore((state) => state.setPlan)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const { request } = useApiClient()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [billingAccount, setBillingAccount] = useState<BillingAccount | null>(null)
  const [activeRecruitmentTenant, setActiveRecruitmentTenant] = useState<string | null>(null)
  const visibleNavigation = appNavigation.filter(
    (item) => (!item.tenantAdminOnly || user?.role === "TENANT_ADMIN")
      && (!item.recruitmentOnly || (publicConfig.recruitmentEnabled && activeRecruitmentTenant === user?.tenantId)),
  )
  const primaryNavigation = visibleNavigation.filter((item) => item.placement !== "footer")
  const footerNavigation = visibleNavigation.filter((item) => item.placement === "footer")
  const pageTitle =
    appNavigation.find(
      (item) => item.href === pathname || (item.href !== "/" && pathname.startsWith(`${item.href}/`)),
    )?.labelKey ?? "dashboard"

  useEffect(() => {
    if (!user?.tenantId) return
    let cancelled = false
    getBillingAccount(request)
      .then((account) => {
        if (cancelled) return
        setBillingAccount(account)
        setPlan(account.planCode)
      })
      .catch(() => {
        if (!cancelled) setBillingAccount(null)
      })
    return () => {
      cancelled = true
    }
  }, [request, setPlan, user?.tenantId])

  useEffect(() => {
    if (!user?.tenantId || !publicConfig.recruitmentEnabled) return
    const controller=new AbortController()
    const tenantId=user.tenantId
    getRecruitmentCapabilities(request,controller.signal).then(value=>setActiveRecruitmentTenant(value.masterEnabled?tenantId:null)).catch(()=>setActiveRecruitmentTenant(null))
    return()=>controller.abort()
  },[request,user?.tenantId])

  async function handleLogout() {
    try {
      await logoutApi()
    } catch {
      // The local session must still be cleared when the server is unavailable.
    }
    clearAuth()
    router.push("/")
  }

  return (
    <div className="min-h-dvh bg-slate-100">
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-60 flex-col bg-slate-900 text-white transition-transform duration-200 xl:translate-x-0",
          sidebarOpen ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex h-14 items-center border-b border-slate-700 px-4">
          <Link href="/" className="flex items-center gap-2" onClick={() => setSidebarOpen(false)}>
            <Image src="/logo.png" alt="CacaNode Logo" width={28} height={28} />
            <span className="text-lg font-bold">CacaNode</span>
          </Link>
          <button
            type="button"
            className="ml-auto rounded-md p-1 text-slate-300 hover:bg-slate-800 xl:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label={t("closeNavigation")}
          >
            <X className="size-5" />
          </button>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto p-3">
          {primaryNavigation.map(({ href, labelKey, icon: Icon, beta }) => {
            const isActive = pathname === href || (href !== "/" && pathname.startsWith(`${href}/`))
            return (
              <Link
                key={href}
                href={href}
                onClick={() => setSidebarOpen(false)}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
                  isActive
                    ? "bg-indigo-600 text-white"
                    : "text-slate-300 hover:bg-slate-800 hover:text-white",
                )}
              >
                <Icon className="size-4" />
                {t(labelKey)}
                {beta && <span className="ml-auto rounded-full bg-slate-950 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-white">{t("beta")}</span>}
              </Link>
            )
          })}
          {mobileNavContent && (
            <div
              className="mt-3 border-t border-slate-700 pt-3 xl:hidden"
              onClick={(event) => {
                const target = event.target as HTMLElement
                if (target.closest("button, a")) setSidebarOpen(false)
              }}
            >
              {mobileNavContent}
            </div>
          )}
        </nav>

        {footerNavigation.map(({ href, labelKey, icon: Icon }) => (
          <div key={href} className="border-t border-slate-700 px-3 py-3">
            <Link
              href={href}
              onClick={() => setSidebarOpen(false)}
              className="group flex items-center gap-3 rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-2.5 transition-colors hover:border-indigo-500/70 hover:bg-slate-800"
            >
              <span className="grid size-8 shrink-0 place-items-center rounded-md bg-indigo-500/15 text-indigo-300 transition-colors group-hover:bg-indigo-500/25 group-hover:text-indigo-200">
                <Icon className="size-4" />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-medium text-slate-100">{t(labelKey)}</span>
                <span className="block text-[11px] text-slate-400">{t("guidesApiSetup")}</span>
              </span>
            </Link>
          </div>
        ))}

        <div className="border-t border-slate-700 p-3">
          <div className="mb-1 flex items-center gap-3 px-3 py-2">
            <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-600 text-xs font-medium">
              {initialsFrom(user?.fullName)}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{user?.fullName ?? t("user")}</p>
              <p className="truncate text-xs text-slate-400">{user?.email ?? ""}</p>
              <div className="mt-1.5">
                {user?.role === "TENANT_ADMIN" ? (
                  <Link
                    href="/settings?tab=quota"
                    aria-label={t("openBillingSettings")}
                    className="inline-flex rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
                  >
                    <PlanStatusBadge
                      plan={billingAccount?.planCode ?? user?.plan}
                      status={billingAccount?.status}
                    />
                  </Link>
                ) : (
                  <PlanStatusBadge
                    plan={billingAccount?.planCode ?? user?.plan}
                    status={billingAccount?.status}
                  />
                )}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={() => void handleLogout()}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-slate-800 hover:text-white"
          >
            <LogOut className="size-4" />
            {t("logout")}
          </button>
        </div>
      </aside>

      {sidebarOpen && (
        <button
          type="button"
          className="fixed inset-0 z-30 bg-black/50 xl:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-label={t("closeNavigation")}
        />
      )}

      <div className="min-h-dvh xl:ml-60">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-slate-200 bg-white px-4">
          <button
            type="button"
            onClick={() => setSidebarOpen(true)}
            className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100 xl:hidden"
            aria-label={t("openNavigation")}
          >
            <Menu className="size-5" />
          </button>
          <h1 className="font-semibold text-slate-800">{t(pageTitle)}</h1>
          <LanguageSwitcher className="ml-auto" />
        </header>
        <main className={cn("p-4 sm:p-6", contentClassName)}>{children}</main>
      </div>
    </div>
  )
}

export function ProtectedAppShell({ children }: { children: ReactNode }) {
  const common = useTranslations("Common")
  const router = useRouter()
  const status = useTokenRehydration()
  const user = useAuthStore((state) => state.user)

  useEffect(() => {
    if (status === "unauthenticated") {
      const destination = `${window.location.pathname}${window.location.search}${window.location.hash}`
      router.replace(withNext("/login", destination))
    }
    if (status === "authenticated" && user?.role === "PLATFORM_ADMIN") {
      router.replace("/platform")
    }
  }, [router, status, user?.role])

  if (status === "rehydrating") {
    return (
      <div className="grid min-h-dvh place-items-center bg-slate-100">
        <Loader2 className="size-8 animate-spin text-indigo-600" aria-label={common("loading")} />
      </div>
    )
  }

  if (status === "unauthenticated" || user?.role === "PLATFORM_ADMIN") {
    return null
  }

  return <AppShell>{children}</AppShell>
}

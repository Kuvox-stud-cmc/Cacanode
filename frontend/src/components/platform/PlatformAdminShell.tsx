"use client"

import Image from "next/image"
import { Activity, BriefcaseBusiness, Building2, CircleAlert, Gauge, Loader2, LogOut, Shield, Users } from "lucide-react"
import { useEffect, type ReactNode } from "react"
import { useTranslations } from "next-intl"
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher"
import { useAuthStore } from "@/components/providers/StoreProvider"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { Link, usePathname, useRouter } from "@/i18n/navigation"
import { logoutApi } from "@/lib/auth-api"
import { withNext } from "@/lib/auth-redirect"
import { publicConfig } from "@/lib/public-config"
import { cn } from "@/lib/utils"

export function PlatformAdminShell({ children }: { children: ReactNode }) {
  const t = useTranslations("Platform")
  const status = useTokenRehydration()
  const user = useAuthStore(state => state.user)
  const clearAuth = useAuthStore(state => state.clearAuth)
  const router = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (status === "unauthenticated") {
      const destination = `${window.location.pathname}${window.location.search}`
      router.replace(withNext("/login", destination))
    } else if (status === "authenticated" && user?.role !== "PLATFORM_ADMIN") {
      router.replace("/dashboard")
    }
  }, [router, status, user?.role])

  async function logout() {
    try { await logoutApi() } catch { /* clear locally regardless */ }
    clearAuth()
    router.replace("/login")
  }

  if (status === "rehydrating") return <div className="grid min-h-dvh place-items-center bg-slate-950"><Loader2 className="size-8 animate-spin text-indigo-300" /></div>
  if (status !== "authenticated" || user?.role !== "PLATFORM_ADMIN") return null

  if (!publicConfig.platformAdministrationEnabled) {
    return <main className="grid min-h-dvh place-items-center bg-slate-950 px-6 text-white"><section className="max-w-xl text-center"><Shield className="mx-auto size-12 text-amber-300" /><h1 className="mt-5 text-2xl font-bold">{t("unavailable.title")}</h1><p className="mt-3 text-slate-300">{t("unavailable.description")}</p><button type="button" onClick={() => void logout()} className="mt-7 rounded-lg bg-white px-5 py-2.5 font-medium text-slate-950">{t("logout")}</button></section></main>
  }

  const navigation = [
    { href: "/platform", label: t("navigation.overview"), icon: Gauge },
    { href: "/platform/tenants", label: t("navigation.tenants"), icon: Building2 },
    ...(publicConfig.recruitmentEnabled ? [{ href: "/platform/jobs", label: t("navigation.jobs"), icon: BriefcaseBusiness }] : []),
    { href: "/platform/operations", label: t("navigation.operations"), icon: Activity },
    { href: "/platform/failures", label: t("navigation.failures"), icon: CircleAlert },
    { href: "/platform/staff", label: t("navigation.staff"), icon: Users },
  ]
  const active = (href: string) => href === "/platform" ? pathname === href : pathname === href || pathname.startsWith(`${href}/`)
  return <div className="min-h-dvh bg-slate-100"><aside className="fixed inset-y-0 left-0 hidden w-64 flex-col bg-slate-950 text-white lg:flex"><div className="flex h-16 items-center gap-2 border-b border-slate-800 px-5"><Image src="/logo.png" alt="CacaNode" width={30} height={30}/><span className="font-bold">CacaNode Platform</span></div><nav className="flex-1 space-y-1 p-3">{navigation.map(({href,label,icon:Icon})=><Link key={href} href={href} className={cn("flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm",active(href)?"bg-indigo-600 text-white":"text-slate-300 hover:bg-slate-900")}><Icon className="size-4"/>{label}</Link>)}</nav><div className="border-t border-slate-800 p-4"><p className="truncate text-sm font-medium">{user.fullName}</p><p className="truncate text-xs text-slate-400">{user.email}</p><button type="button" onClick={() => void logout()} className="mt-3 flex w-full items-center gap-2 rounded-md py-2 text-sm text-slate-300 hover:text-white"><LogOut className="size-4"/>{t("logout")}</button></div></aside><div className="lg:ml-64"><header className="flex h-16 items-center border-b bg-white px-4 sm:px-6"><Link href="/platform" className="font-bold lg:hidden">CacaNode Platform</Link><nav className="ml-4 flex gap-1 overflow-x-auto lg:hidden">{navigation.map(({href,label})=><Link key={href} href={href} className={cn("rounded px-2 py-1 text-sm whitespace-nowrap",active(href)?"bg-indigo-50 text-indigo-700":"text-slate-600")}>{label}</Link>)}</nav><LanguageSwitcher className="ml-auto" /></header><main className="p-4 sm:p-6">{children}</main></div></div>
}

"use client"

import { useEffect, useState, type ReactNode } from "react"
import Image from "next/image"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { Loader2, LogOut, Menu, X } from "lucide-react"
import { useAuthStore } from "@/components/providers/StoreProvider"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { logoutApi } from "@/lib/auth-api"
import { appNavigation } from "@/components/app/navigation"
import { cn } from "@/lib/utils"

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
}

export function AppShell({ children, contentClassName }: AppShellProps) {
  const pathname = usePathname()
  const router = useRouter()
  const user = useAuthStore((state) => state.user)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const pageTitle =
    appNavigation.find((item) => item.href === pathname)?.label ?? "Dashboard"

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
          "fixed inset-y-0 left-0 z-40 flex w-60 flex-col bg-slate-900 text-white transition-transform duration-200 lg:translate-x-0",
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
            className="ml-auto rounded-md p-1 text-slate-300 hover:bg-slate-800 lg:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close navigation"
          >
            <X className="size-5" />
          </button>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto p-3">
          {appNavigation.map(({ href, label, icon: Icon }) => {
            const isActive = pathname === href
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
                {label}
              </Link>
            )
          })}
        </nav>

        <div className="border-t border-slate-700 p-3">
          <div className="mb-1 flex items-center gap-3 px-3 py-2">
            <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-600 text-xs font-medium">
              {initialsFrom(user?.fullName)}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{user?.fullName ?? "User"}</p>
              <p className="truncate text-xs text-slate-400">{user?.email ?? ""}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => void handleLogout()}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-slate-800 hover:text-white"
          >
            <LogOut className="size-4" />
            Log out
          </button>
        </div>
      </aside>

      {sidebarOpen && (
        <button
          type="button"
          className="fixed inset-0 z-30 bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-label="Close navigation"
        />
      )}

      <div className="min-h-dvh lg:ml-60">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-slate-200 bg-white px-4">
          <button
            type="button"
            onClick={() => setSidebarOpen(true)}
            className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100 lg:hidden"
            aria-label="Open navigation"
          >
            <Menu className="size-5" />
          </button>
          <h1 className="font-semibold text-slate-800">{pageTitle}</h1>
        </header>
        <main className={cn("p-6", contentClassName)}>{children}</main>
      </div>
    </div>
  )
}

export function ProtectedAppShell({ children }: { children: ReactNode }) {
  const router = useRouter()
  const status = useTokenRehydration()

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login")
    }
  }, [router, status])

  if (status === "rehydrating") {
    return (
      <div className="grid min-h-dvh place-items-center bg-slate-100">
        <Loader2 className="size-8 animate-spin text-indigo-600" aria-label="Loading" />
      </div>
    )
  }

  if (status === "unauthenticated") {
    return null
  }

  return <AppShell>{children}</AppShell>
}

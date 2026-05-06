"use client"

import { useEffect } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { useAuthStore, useUIStore } from "@/components/providers/StoreProvider"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { logoutApi } from "@/lib/auth-api"
import {
  LayoutDashboard,
  FileText,
  MessageSquare,
  BarChart2,
  Users,
  Settings,
  LogOut,
  Menu,
  X,
  Loader2,
} from "lucide-react"

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/documents", label: "Documents", icon: FileText },
  { href: "/conversations", label: "Conversations", icon: MessageSquare },
  { href: "/analytics", label: "Analytics", icon: BarChart2 },
  { href: "/users", label: "Users", icon: Users },
  { href: "/settings", label: "Settings", icon: Settings },
]

function initialsFrom(fullName: string | undefined): string {
  if (!fullName?.trim()) return "?"
  const parts = fullName.trim().split(/\s+/)
  if (parts.length >= 2) {
    return (parts[0]![0] + parts[parts.length - 1]![0]).toUpperCase()
  }
  return fullName.slice(0, 2).toUpperCase()
}

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const pathname = usePathname()
  const router = useRouter()
  const status = useTokenRehydration()
  const user = useAuthStore((s) => s.user)
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const sidebarOpen = useUIStore((s) => s.sidebarOpen)
  const toggleSidebar = useUIStore((s) => s.toggleSidebar)

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login")
    }
  }, [status, router])

  const pageTitle =
    navItems.find((item) => item.href === pathname)?.label ?? "Dashboard"

  if (status === "rehydrating") {
    return (
      <div className="min-h-screen grid place-items-center bg-slate-100">
        <Loader2 className="w-8 h-8 animate-spin text-indigo-600" aria-label="Loading" />
      </div>
    )
  }

  if (status === "unauthenticated") {
    return null
  }

  async function handleLogout() {
    try {
      await logoutApi()
    } catch {
      // still clear local session
    }
    clearAuth()
    router.push("/login")
  }

  return (
    <div className="min-h-screen bg-slate-100">
      <aside
        className={`fixed top-0 left-0 h-full w-60 bg-slate-900 text-white flex flex-col z-40 transition-transform duration-200 ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="p-4 border-b border-slate-700">
          <div className="flex items-center gap-2">
            <img src="/logo.png" alt="CacaNode Logo" className="w-7 h-7" />
            <span className="font-bold text-lg">CacaNode</span>
          </div>
        </div>

        <nav className="flex-1 p-3 space-y-1">
          {navItems.map(({ href, label, icon: Icon }) => {
            const isActive = pathname === href
            return (
              <Link
                key={href}
                href={href}
                className={`flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${
                  isActive
                    ? "bg-indigo-600 text-white"
                    : "text-slate-300 hover:bg-slate-800 hover:text-white"
                }`}
              >
                <Icon className="w-4 h-4" />
                {label}
              </Link>
            )
          })}
        </nav>

        <div className="p-3 border-t border-slate-700">
          <div className="flex items-center gap-3 px-3 py-2 mb-1">
            <div className="w-7 h-7 bg-slate-600 rounded-full flex items-center justify-center text-xs font-medium shrink-0">
              {initialsFrom(user?.fullName)}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate">
                {user?.fullName ?? "User"}
              </p>
              <p className="text-xs text-slate-400 truncate">
                {user?.email ?? ""}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => void handleLogout()}
            className="flex items-center gap-3 px-3 py-2 rounded-md text-sm text-slate-300 hover:bg-slate-800 hover:text-white w-full transition-colors"
          >
            <LogOut className="w-4 h-4" />
            Log out
          </button>
        </div>
      </aside>

      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 lg:hidden"
          onClick={toggleSidebar}
          aria-hidden
        />
      )}

      <div
        className={`transition-all duration-200 ${sidebarOpen ? "lg:ml-60" : "ml-0"}`}
      >
        <header className="sticky top-0 z-20 bg-white border-b border-slate-200 px-4 h-14 flex items-center gap-3">
          <button
            type="button"
            onClick={toggleSidebar}
            className="p-1.5 rounded-md hover:bg-slate-100 text-slate-600"
          >
            {sidebarOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
          <h1 className="font-semibold text-slate-800">{pageTitle}</h1>
        </header>

        <main className="p-6">{children}</main>
      </div>
    </div>
  )
}

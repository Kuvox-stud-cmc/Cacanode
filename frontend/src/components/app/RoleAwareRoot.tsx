"use client"

import { Loader2 } from "lucide-react"
import { useEffect } from "react"
import ChatPlayground from "@/components/chat/ChatPlayground"
import { useAuthStore } from "@/components/providers/StoreProvider"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { useRouter } from "@/i18n/navigation"

export function RoleAwareRoot() {
  const status = useTokenRehydration()
  const role = useAuthStore(state => state.user?.role)
  const router = useRouter()

  useEffect(() => {
    if (status === "authenticated" && role === "PLATFORM_ADMIN") router.replace("/platform")
  }, [role, router, status])

  if (status === "rehydrating" || role === "PLATFORM_ADMIN") {
    return <div className="grid min-h-dvh place-items-center bg-slate-100"><Loader2 className="size-8 animate-spin text-indigo-600" /></div>
  }
  return <ChatPlayground />
}

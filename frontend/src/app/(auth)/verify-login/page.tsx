"use client"

import { Suspense, useEffect, useState } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import Link from "next/link"
import { AlertCircle, CheckCircle, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card"
import { useAuthStore } from "@/components/providers/StoreProvider"
import { verifyLogin2FAApi } from "@/lib/auth-api"

function VerifyLoginContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const token = searchParams.get("token")
  const setAuth = useAuthStore((s) => s.setAuth)

  const [status, setStatus] = useState<"loading" | "success" | "error">(
    token ? "loading" : "error",
  )
  const [errorMessage, setErrorMessage] = useState(
    token ? "" : "Invalid or missing verification token",
  )

  useEffect(() => {
    if (!token) return

    const verify = async () => {
      try {
        const res = await verifyLogin2FAApi(token)
        setAuth(res.user, res.accessToken, res.user.tenantId)
        setStatus("success")
        // Redirect to dashboard after a short delay
        setTimeout(() => {
          router.push("/dashboard")
        }, 1500)
      } catch (e) {
        const msg = e instanceof Error ? e.message : "Failed to verify login"
        setErrorMessage(msg)
        setStatus("error")
      }
    }

    verify()
  }, [token, router, setAuth])

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-8">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-2 mb-2">
            <img src="/logo.png" alt="" className="w-8 h-8" width={32} height={32} />
            <span className="text-2xl font-bold bg-gradient-to-r from-indigo-600 to-violet-600 bg-clip-text text-transparent">
              CacaNode
            </span>
          </div>
          <p className="text-slate-500 text-sm">AI-powered customer support</p>
        </div>

        <Card className="shadow-md bg-white">
          <CardHeader className="text-center">
            {status === "loading" && (
              <>
                <div className="mx-auto w-12 h-12 bg-indigo-100 rounded-full flex items-center justify-center mb-4">
                  <Loader2 className="w-6 h-6 text-indigo-600 animate-spin" />
                </div>
                <CardTitle>Verifying your login...</CardTitle>
                <CardDescription>
                  Please wait while we verify your identity
                </CardDescription>
              </>
            )}

            {status === "success" && (
              <>
                <div className="mx-auto w-12 h-12 bg-green-100 rounded-full flex items-center justify-center mb-4">
                  <CheckCircle className="w-6 h-6 text-green-600" />
                </div>
                <CardTitle>Login verified!</CardTitle>
                <CardDescription>
                  Redirecting you to your dashboard...
                </CardDescription>
              </>
            )}

            {status === "error" && (
              <>
                <div className="mx-auto w-12 h-12 bg-red-100 rounded-full flex items-center justify-center mb-4">
                  <AlertCircle className="w-6 h-6 text-red-600" />
                </div>
                <CardTitle>Verification failed</CardTitle>
                <CardDescription>
                  {errorMessage || "The verification link is invalid or has expired"}
                </CardDescription>
              </>
            )}
          </CardHeader>

          <CardContent className="space-y-4">
            {status === "error" && (
              <Link href="/login">
                <Button variant="outline" className="w-full">
                  Back to login
                </Button>
              </Link>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

export default function VerifyLoginPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-slate-50" />}>
      <VerifyLoginContent />
    </Suspense>
  )
}

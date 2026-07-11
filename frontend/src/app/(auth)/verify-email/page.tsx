"use client"

import { useEffect, useState, Suspense } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import Link from "next/link"
import { Loader2, CheckCircle, XCircle } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card"
import { useAuthStore } from "@/components/providers/StoreProvider"
import { verifyEmailApi } from "@/lib/auth-api"

function VerifyEmailContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const setAuth = useAuthStore((s) => s.setAuth)
  const token = searchParams.get("token")
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    token ? "loading" : "error",
  )
  const [error, setError] = useState<string>(
    token ? "" : "No verification token found in the URL.",
  )

  useEffect(() => {
    if (!token) return

    verifyEmailApi(token)
      .then((res) => {
        setAuth(res.user, res.accessToken, res.user.tenantId)
        setStatus("success")
        // Redirect to dashboard after a brief delay
        setTimeout(() => {
          router.push("/dashboard")
        }, 1500)
      })
      .catch((e) => {
        setStatus("error")
        setError(e instanceof Error ? e.message : "Verification failed. Please try again.")
      })
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
                <CardTitle>Verifying your email...</CardTitle>
                <CardDescription>
                  Please wait while we activate your account
                </CardDescription>
              </>
            )}
            {status === "success" && (
              <>
                <div className="mx-auto w-12 h-12 bg-green-100 rounded-full flex items-center justify-center mb-4">
                  <CheckCircle className="w-6 h-6 text-green-600" />
                </div>
                <CardTitle>Email verified!</CardTitle>
                <CardDescription>
                  Your account has been activated. Redirecting to dashboard...
                </CardDescription>
              </>
            )}
            {status === "error" && (
              <>
                <div className="mx-auto w-12 h-12 bg-red-100 rounded-full flex items-center justify-center mb-4">
                  <XCircle className="w-6 h-6 text-red-600" />
                </div>
                <CardTitle>Verification failed</CardTitle>
                <CardDescription>{error}</CardDescription>
              </>
            )}
          </CardHeader>
          <CardContent>
            {status === "error" && (
              <div className="flex flex-col gap-3">
                <Link href="/login">
                  <Button className="w-full">Go to login</Button>
                </Link>
                <Link href="/register">
                  <Button variant="outline" className="w-full">
                    Create new account
                  </Button>
                </Link>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-8">
        <div className="w-full max-w-md">
          <Card className="shadow-md bg-white">
            <CardHeader className="text-center">
              <div className="mx-auto w-12 h-12 bg-indigo-100 rounded-full flex items-center justify-center mb-4">
                <Loader2 className="w-6 h-6 text-indigo-600 animate-spin" />
              </div>
              <CardTitle>Loading...</CardTitle>
            </CardHeader>
          </Card>
        </div>
      </div>
    }>
      <VerifyEmailContent />
    </Suspense>
  )
}

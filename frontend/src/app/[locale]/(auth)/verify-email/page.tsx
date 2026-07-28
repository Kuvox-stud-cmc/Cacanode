"use client"


import { useEffect, useState, Suspense } from "react"
import { useTranslations } from "next-intl"
import { useSearchParams } from "next/navigation"
import { Link, useRouter } from "@/i18n/navigation"
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
import { authApiErrorMessage, verifyEmailApi } from "@/lib/auth-api"
import {
  consumeAuthDestinationForRole,
  rememberAuthDestination,
  safeInternalPath,
  withNext,
} from "@/lib/auth-redirect"

function VerifyEmailContent() {
  const t = useTranslations("Auth")
  const router = useRouter()
  const searchParams = useSearchParams()
  const setAuth = useAuthStore((s) => s.setAuth)
  const token = searchParams.get("token")
  const next = safeInternalPath(searchParams.get("next"))
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    token ? "loading" : "error",
  )
  const [error, setError] = useState<string>(
    token ? "" : t("verifyEmail.missingToken"),
  )

  useEffect(() => {
    if (!token) return

    rememberAuthDestination(next)

    verifyEmailApi(token)
      .then((res) => {
        setAuth(res.user, res.accessToken, res.user.tenantId)
        setStatus("success")
        // Redirect to dashboard after a brief delay
        setTimeout(() => {
          router.push(consumeAuthDestinationForRole(res.user.role))
        }, 1500)
      })
      .catch((e) => {
        setStatus("error")
        setError(authApiErrorMessage(e, t("fallback.verificationFailed")))
      })
  }, [token, next, router, setAuth, t])

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
          <p className="text-slate-500 text-sm">{t("tagline")}</p>
        </div>

        <Card className="shadow-md bg-white">
          <CardHeader className="text-center">
            {status === "loading" && (
              <>
                <div className="mx-auto w-12 h-12 bg-indigo-100 rounded-full flex items-center justify-center mb-4">
                  <Loader2 className="w-6 h-6 text-indigo-600 animate-spin" />
                </div>
                <CardTitle>{t("verifyEmail.verifying")}</CardTitle>
                <CardDescription>{t("verifyEmail.activating")}</CardDescription>
              </>
            )}
            {status === "success" && (
              <>
                <div className="mx-auto w-12 h-12 bg-green-100 rounded-full flex items-center justify-center mb-4">
                  <CheckCircle className="w-6 h-6 text-green-600" />
                </div>
                <CardTitle>{t("verifyEmail.success")}</CardTitle>
                <CardDescription>{t("verifyEmail.redirecting")}</CardDescription>
              </>
            )}
            {status === "error" && (
              <>
                <div className="mx-auto w-12 h-12 bg-red-100 rounded-full flex items-center justify-center mb-4">
                  <XCircle className="w-6 h-6 text-red-600" />
                </div>
                <CardTitle>{t("verificationFailed")}</CardTitle>
                <CardDescription>{error}</CardDescription>
              </>
            )}
          </CardHeader>
          <CardContent>
            {status === "error" && (
              <div className="flex flex-col gap-3">
                <Link href={withNext("/login", next)}>
                  <Button className="w-full">{t("goToLogin")}</Button>
                </Link>
                <Link href={withNext("/register", next)}>
                  <Button variant="outline" className="w-full">
                    {t("createNewAccount")}
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
  const t = useTranslations("Auth")
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-8">
        <div className="w-full max-w-md">
          <Card className="shadow-md bg-white">
            <CardHeader className="text-center">
              <div className="mx-auto w-12 h-12 bg-indigo-100 rounded-full flex items-center justify-center mb-4">
                <Loader2 className="w-6 h-6 text-indigo-600 animate-spin" />
              </div>
              <CardTitle>{t("loading")}</CardTitle>
            </CardHeader>
          </Card>
        </div>
      </div>
    }>
      <VerifyEmailContent />
    </Suspense>
  )
}

"use client"

import { useState, useEffect, useCallback } from "react"
import { useSearchParams } from "next/navigation"
import Link from "next/link"
import { Mail, ArrowLeft, Loader2, AlertCircle, CheckCircle } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card"
import { resendLogin2FAApi } from "@/lib/auth-api"

const COOLDOWN_SECONDS = 60

export default function CheckLoginEmailPage() {
  const searchParams = useSearchParams()
  const email = searchParams.get("email") || "your email"

  const [countdown, setCountdown] = useState(COOLDOWN_SECONDS)
  const [isResending, setIsResending] = useState(false)
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null)
  const [isSuspended, setIsSuspended] = useState(false)

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000)
      return () => clearTimeout(timer)
    }
  }, [countdown])

  const handleResend = useCallback(async () => {
    if (countdown > 0 || isResending || isSuspended) return

    setIsResending(true)
    setMessage(null)

    try {
      const res = await resendLogin2FAApi(email)
      setCountdown(COOLDOWN_SECONDS)
      setMessage({ type: "success", text: res.message })
    } catch (e) {
      const errorMsg = e instanceof Error ? e.message : "Failed to resend verification email"

      if (errorMsg.includes("suspended")) {
        setIsSuspended(true)
        setMessage({ type: "error", text: errorMsg })
      } else {
        setMessage({ type: "error", text: errorMsg })
      }
    } finally {
      setIsResending(false)
    }
  }, [countdown, email, isResending, isSuspended])

  if (isSuspended) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-8">
        <div className="w-full max-w-md">
          <Card className="shadow-md bg-white">
            <CardHeader className="text-center">
              <div className="mx-auto w-12 h-12 bg-red-100 rounded-full flex items-center justify-center mb-4">
                <AlertCircle className="w-6 h-6 text-red-600" />
              </div>
              <CardTitle>Account Suspended</CardTitle>
              <CardDescription>
                Your account has been suspended due to verification abuse
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-slate-600 text-center">
                {message?.text || "Please contact support for assistance."}
              </p>
              <Link href="/login">
                <Button variant="outline" className="w-full">
                  <ArrowLeft className="w-4 h-4 mr-2" />
                  Back to login
                </Button>
              </Link>
            </CardContent>
          </Card>
        </div>
      </div>
    )
  }

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
            <div className="mx-auto w-12 h-12 bg-indigo-100 rounded-full flex items-center justify-center mb-4">
              <Mail className="w-6 h-6 text-indigo-600" />
            </div>
            <CardTitle>Verify your login</CardTitle>
            <CardDescription>
              We&apos;ve sent a verification link to
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="text-center">
              <p className="font-medium text-slate-900">{email}</p>
            </div>

            {message && (
              <div
                className={`flex items-start gap-2 p-3 rounded-lg text-sm ${
                  message.type === "success"
                    ? "bg-green-50 text-green-700"
                    : "bg-red-50 text-red-700"
                }`}
              >
                {message.type === "success" ? (
                  <CheckCircle className="w-4 h-4 mt-0.5 shrink-0" />
                ) : (
                  <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                )}
                {message.text}
              </div>
            )}

            <div className="bg-slate-50 rounded-lg p-4 text-sm text-slate-600">
              <p className="mb-2">
                Click the link in the email to complete your login. The link will expire in 15 minutes.
              </p>
              <p>
                If you don&apos;t see the email, check your spam folder and click the button below to resend.
              </p>
            </div>

            <Button
              onClick={handleResend}
              disabled={countdown > 0 || isResending}
              className="w-full"
            >
              {isResending ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  Sending...
                </>
              ) : countdown > 0 ? (
                <>Resend email ({countdown}s)</>
              ) : (
                "Resend verification email"
              )}
            </Button>

            <Link href="/login">
              <Button variant="outline" className="w-full">
                <ArrowLeft className="w-4 h-4 mr-2" />
                Back to login
              </Button>
            </Link>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

"use client";


import { Suspense, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, useRouter } from "@/i18n/navigation";
import { AlertCircle, Eye, EyeOff, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { authApiErrorMessage, registerApi } from "@/lib/auth-api";
import { cn } from "@/lib/utils";
import {
  rememberAuthDestination,
  safeInternalPath,
  withNext,
} from "@/lib/auth-redirect";

type RegisterForm = { companyName: string; fullName: string; email: string; password: string; confirmPassword: string };

function RegisterContent() {
  const t = useTranslations("Auth");
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeInternalPath(searchParams.get("next"));
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const registerSchema = z.object({
    companyName: z.string().min(1, t("validation.companyRequired")),
    fullName: z.string().min(1, t("validation.fullNameRequired")),
    email: z.string().min(1, t("validation.emailRequired")).email(t("validation.invalidEmail")),
    password: z.string().min(8, t("validation.passwordLength")),
    confirmPassword: z.string().min(1, t("validation.confirmPasswordRequired")),
  }).refine((data) => data.password === data.confirmPassword, {
    message: t("validation.passwordsMismatch"),
    path: ["confirmPassword"],
  });

  useEffect(() => {
    rememberAuthDestination(next);
  }, [next]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      companyName: "",
      fullName: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const clearApiError = () => setApiError(null);

  const onSubmit = async (data: RegisterForm) => {
    setApiError(null);
    try {
      await registerApi({
        companyName: data.companyName,
        fullName: data.fullName,
        email: data.email,
        password: data.password,
      });
      router.push(
        withNext(`/check-email?email=${encodeURIComponent(data.email)}`, next),
      );
    } catch (e) {
      const msg = authApiErrorMessage(e, t("fallback.somethingWentWrong"));
      setApiError(msg);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-8">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-2 mb-2">
            <img
              src="/logo.png"
              alt=""
              className="w-8 h-8"
              width={32}
              height={32}
            />
            <span className="text-2xl font-bold bg-gradient-to-r from-indigo-600 to-violet-600 bg-clip-text text-transparent">
              CacaNode
            </span>
          </div>
          <p className="text-slate-500 text-sm">{t("tagline")}</p>
        </div>

        <Card className="shadow-md bg-white">
          <CardHeader>
            <CardTitle>{t("register.title")}</CardTitle>
            <CardDescription>{t("register.description")}</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="companyName">{t("companyName")}</Label>
                <Input
                  id="companyName"
                  placeholder="Acme Corp"
                  disabled={isSubmitting}
                  aria-invalid={!!errors.companyName}
                  {...register("companyName", { onChange: clearApiError })}
                />
                {errors.companyName && (
                  <p className="text-red-600 text-xs">
                    {errors.companyName.message}
                  </p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="fullName">{t("fullName")}</Label>
                <Input
                  id="fullName"
                  placeholder="Jane Doe"
                  disabled={isSubmitting}
                  aria-invalid={!!errors.fullName}
                  {...register("fullName", { onChange: clearApiError })}
                />
                {errors.fullName && (
                  <p className="text-red-600 text-xs">
                    {errors.fullName.message}
                  </p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="email">{t("email")}</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="jane@company.com"
                  disabled={isSubmitting}
                  aria-invalid={!!errors.email}
                  {...register("email", { onChange: clearApiError })}
                />
                {errors.email && (
                  <p className="text-red-600 text-xs">{errors.email.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="password">{t("password")}</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder={t("register.passwordPlaceholder")}
                    disabled={isSubmitting}
                    className="pr-10"
                    aria-invalid={!!errors.password}
                    {...register("password", { onChange: clearApiError })}
                  />
                  <button
                    type="button"
                    tabIndex={-1}
                    disabled={isSubmitting}
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-800 p-1 rounded-md disabled:opacity-50"
                    aria-label={
                      showPassword ? t("hidePassword") : t("showPassword")
                    }
                  >
                    {showPassword ? (
                      <EyeOff className="w-4 h-4" />
                    ) : (
                      <Eye className="w-4 h-4" />
                    )}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-red-600 text-xs">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="confirmPassword">{t("confirmPassword")}</Label>
                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type={showConfirm ? "text" : "password"}
                    placeholder={t("register.confirmPlaceholder")}
                    disabled={isSubmitting}
                    className="pr-10"
                    aria-invalid={!!errors.confirmPassword}
                    {...register("confirmPassword", {
                      onChange: clearApiError,
                    })}
                  />
                  <button
                    type="button"
                    tabIndex={-1}
                    disabled={isSubmitting}
                    onClick={() => setShowConfirm((v) => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-800 p-1 rounded-md disabled:opacity-50"
                    aria-label={showConfirm ? t("hidePassword") : t("showPassword")}
                  >
                    {showConfirm ? (
                      <EyeOff className="w-4 h-4" />
                    ) : (
                      <Eye className="w-4 h-4" />
                    )}
                  </button>
                </div>
                {errors.confirmPassword && (
                  <p className="text-red-600 text-xs">
                    {errors.confirmPassword.message}
                  </p>
                )}
              </div>

              {apiError && (
                <div
                  role="alert"
                  className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
                >
                  <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                  <p className="flex-1">{apiError}</p>
                  <button
                    type="button"
                    onClick={() => setApiError(null)}
                    className="text-red-700 hover:text-red-900 p-0.5 rounded"
                    aria-label={t("dismissError")}
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              )}

              <Button
                type="submit"
                disabled={isSubmitting}
                className={cn(
                  "w-full h-10 bg-indigo-600 hover:bg-indigo-700 text-white",
                  isSubmitting && "opacity-70",
                )}
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    {t("register.creating")}
                  </>
                ) : (
                  t("register.create")
                )}
              </Button>
            </form>

            <p className="text-center text-sm text-slate-500 mt-4">
              {t("register.haveAccount")}{" "}
              <Link
                href={withNext("/login", next)}
                className="text-indigo-600 hover:underline font-medium"
              >
                {t("login.signIn")}
              </Link>
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-slate-50" />}>
      <RegisterContent />
    </Suspense>
  );
}

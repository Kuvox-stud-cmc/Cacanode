"use client";

import { Suspense, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
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
import { registerApi } from "@/lib/auth-api";
import { cn } from "@/lib/utils";
import {
  rememberAuthDestination,
  safeInternalPath,
  withNext,
} from "@/lib/auth-redirect";

const registerSchema = z
  .object({
    companyName: z.string().min(1, "Company name is required"),
    fullName: z.string().min(1, "Full name is required"),
    email: z
      .string()
      .min(1, "Email is required")
      .email("Invalid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string().min(1, "Please confirm your password"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type RegisterForm = z.infer<typeof registerSchema>;

function RegisterContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeInternalPath(searchParams.get("next"));
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

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
      const msg = e instanceof Error ? e.message : "Something went wrong";
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
          <p className="text-slate-500 text-sm">AI-powered customer support</p>
        </div>

        <Card className="shadow-md bg-white">
          <CardHeader>
            <CardTitle>Create your account</CardTitle>
            <CardDescription>Get started with CacaNode today</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="companyName">Company name</Label>
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
                <Label htmlFor="fullName">Full name</Label>
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
                <Label htmlFor="email">Email</Label>
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
                <Label htmlFor="password">Password</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder="Min. 8 characters"
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
                      showPassword ? "Hide password" : "Show password"
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
                <Label htmlFor="confirmPassword">Confirm password</Label>
                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type={showConfirm ? "text" : "password"}
                    placeholder="Re-enter password"
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
                    aria-label={showConfirm ? "Hide password" : "Show password"}
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
                    aria-label="Dismiss error"
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
                    Creating account...
                  </>
                ) : (
                  "Create account"
                )}
              </Button>
            </form>

            <p className="text-center text-sm text-slate-500 mt-4">
              Already have an account?{" "}
              <Link
                href={withNext("/login", next)}
                className="text-indigo-600 hover:underline font-medium"
              >
                Sign in
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

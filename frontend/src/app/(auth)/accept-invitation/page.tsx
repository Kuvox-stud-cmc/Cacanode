"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AlertCircle, CheckCircle2, Loader2, Users } from "lucide-react";
import { acceptInvitationApi, validateInvitationApi } from "@/lib/auth-api";
import { useAuthStore } from "@/components/providers/StoreProvider";
import type { InvitationValidation } from "@/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z.object({
  fullName: z.string().trim().min(1, "Enter your full name").max(255),
  password: z.string().min(8, "Password must be at least 8 characters").max(128),
  confirmPassword: z.string(),
}).refine(values => values.password === values.confirmPassword, {
  path: ["confirmPassword"], message: "Passwords do not match",
});
type FormValues = z.infer<typeof schema>;

function AcceptInvitationContent() {
  const token = useSearchParams().get("token") ?? "";
  const router = useRouter();
  const setAuth = useAuthStore(state => state.setAuth);
  const [invitation, setInvitation] = useState<InvitationValidation | null>(null);
  const [validating, setValidating] = useState(Boolean(token));
  const [pageError, setPageError] = useState<string | null>(token ? null : "This invitation link is missing its token.");
  const [accepted, setAccepted] = useState(false);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormValues>({ resolver: zodResolver(schema) });

  useEffect(() => {
    let active = true;
    if (!token) return;
    validateInvitationApi(token).then(value => { if (active) setInvitation(value); })
      .catch(error => { if (active) setPageError(error instanceof Error ? error.message : "This invitation is invalid or expired."); })
      .finally(() => { if (active) setValidating(false); });
    return () => { active = false; };
  }, [token]);

  const submit = async (values: FormValues) => {
    setPageError(null);
    try {
      const auth = await acceptInvitationApi({ token, fullName: values.fullName, password: values.password });
      setAuth(auth.user, auth.accessToken, auth.user.tenantId);
      setAccepted(true);
      window.setTimeout(() => router.replace("/dashboard"), 500);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "Unable to accept this invitation.");
    }
  };

  return <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10"><Card className="w-full max-w-md shadow-md"><CardHeader className="text-center"><div className="mx-auto mb-3 flex size-12 items-center justify-center rounded-full bg-indigo-100">{accepted ? <CheckCircle2 className="size-6 text-green-600" /> : <Users className="size-6 text-indigo-600" />}</div><CardTitle>{accepted ? "Invitation accepted" : "Join your team"}</CardTitle></CardHeader><CardContent>
    {validating ? <div className="flex items-center justify-center gap-2 py-8 text-sm text-slate-500"><Loader2 className="size-4 animate-spin" />Validating invitation...</div> : accepted ? <p className="py-6 text-center text-sm text-slate-600">Your account is ready. Taking you to the dashboard...</p> : pageError && !invitation ? <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-center text-sm text-red-800"><AlertCircle className="mx-auto mb-2 size-5" />{pageError}</div> : invitation && <>
      <div className="mb-5 rounded-lg bg-slate-50 p-3 text-sm text-slate-600"><p>You were invited as <strong>{invitation.role === "TENANT_ADMIN" ? "Tenant Admin" : "User"}</strong> to <strong>{invitation.tenantName}</strong>.</p><p className="mt-1 text-xs text-slate-500">{invitation.email}</p></div>
      <form onSubmit={handleSubmit(submit)} className="space-y-4"><div className="space-y-1.5"><Label htmlFor="fullName">Full name</Label><Input id="fullName" autoComplete="name" {...register("fullName")} />{errors.fullName && <p className="text-xs text-red-600">{errors.fullName.message}</p>}</div><div className="space-y-1.5"><Label htmlFor="password">Password</Label><Input id="password" type="password" autoComplete="new-password" {...register("password")} />{errors.password && <p className="text-xs text-red-600">{errors.password.message}</p>}</div><div className="space-y-1.5"><Label htmlFor="confirmPassword">Confirm password</Label><Input id="confirmPassword" type="password" autoComplete="new-password" {...register("confirmPassword")} />{errors.confirmPassword && <p className="text-xs text-red-600">{errors.confirmPassword.message}</p>}</div>{pageError && <div role="alert" className="flex gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"><AlertCircle className="mt-0.5 size-4 shrink-0" />{pageError}</div>}<Button type="submit" disabled={isSubmitting} className="w-full bg-indigo-600 text-white hover:bg-indigo-700">{isSubmitting ? <><Loader2 className="size-4 animate-spin" />Creating account...</> : "Accept invitation"}</Button></form>
    </>}
  </CardContent></Card></div>;
}

export default function AcceptInvitationPage() {
  return <Suspense fallback={<div className="min-h-screen bg-slate-50" />}><AcceptInvitationContent /></Suspense>;
}

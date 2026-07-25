"use client";

import { useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useApiClient } from "@/hooks/useApiClient";
import { getBillingAccount, type BillingAccount } from "@/lib/billing-api";
import { Progress } from "@/components/ui/progress";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Link } from "@/i18n/navigation";

export function RecruitmentUsagePage() {
  const t = useTranslations("Recruitment"); const format = useFormatter(); const { request } = useApiClient();
  const [account, setAccount] = useState<BillingAccount | null>(null); const [error, setError] = useState("");
  useEffect(() => { getBillingAccount(request).then(setAccount).catch((cause) => setError(cause instanceof Error ? cause.message : t("loadError"))); }, [request, t]);
  const groups = account ? [
    { key: "activeJobs", value: account.activeJobs }, { key: "verifiedApplications", value: account.verifiedApplications },
    { key: "interviewSeconds", value: account.interviewSeconds }, { key: "cvAnalyses", value: account.cvAnalyses },
    { key: "recruitmentStorageBytes", value: account.recruitmentStorageBytes },
  ] as const : [];
  return <div className="space-y-4" aria-live="polite"><div><h3 className="text-lg font-semibold">{t("usage.title")}</h3><p className="text-sm text-slate-500">{account ? t("usage.reset", { date: format.dateTime(new Date(account.nextQuotaResetAt), { dateStyle: "long" }) }) : t("loading")}</p></div>{error && <p role="alert" className="text-red-700">{error}</p>}<div className="grid gap-4 sm:grid-cols-2">{groups.map(({ key, value }) => { const total = value.used + value.reserved; const percent = value.limit === 0 ? (total ? 100 : 0) : Math.min(100, total / value.limit * 100); return <Card key={key}><CardHeader><CardTitle className="text-sm">{t(`usage.${key}`)}</CardTitle></CardHeader><CardContent className="space-y-3"><div className="flex justify-between text-sm"><span>{t("usage.usedReserved", { used: format.number(value.used), reserved: format.number(value.reserved) })}</span><strong>{format.number(value.limit)}</strong></div><Progress value={percent} />{value.overLimit && <p className="text-xs text-red-700">{t("usage.overLimit")}</p>}</CardContent></Card>; })}</div><Button render={<Link href="/settings?tab=quota" />}>{t("usage.upgrade")}</Button></div>;
}

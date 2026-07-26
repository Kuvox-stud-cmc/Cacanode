"use client";

import { useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useApiClient } from "@/hooks/useApiClient";
import { getRecruitmentOverview, type RecruitmentOverview } from "@/lib/recruitment-admin-api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";

const countGroups = ["jobs", "applications", "interviews"] as const;

export function RecruitmentOverviewPage() {
  const t = useTranslations("Recruitment");
  const format = useFormatter();
  const { request } = useApiClient();
  const [data, setData] = useState<RecruitmentOverview | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    getRecruitmentOverview(request, controller.signal).then(setData).catch((cause) => {
      if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : t("loadError"));
    });
    return () => controller.abort();
  }, [request, t]);

  const groups = data ? [data.jobStatusCounts, data.applicationStatusCounts, data.interviewStatusCounts] : [];
  return <div className="space-y-5" aria-live="polite">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h3 className="text-lg font-semibold">{t("overview.title")}</h3><p className="text-sm text-slate-500">{t("overview.description")}</p></div>
      <div className="flex gap-2"><Button nativeButton={false} render={<Link href="/recruitment/jobs" />}>{t("overview.manageJobs")}</Button><Button nativeButton={false} variant="outline" render={<Link href="/recruitment/applications" />}>{t("overview.reviewApplications")}</Button></div>
    </div>
    {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <div className="grid gap-4 md:grid-cols-3">{countGroups.map((name, index) => <Card key={name}><CardHeader><CardTitle className="text-base">{t(`nav.${name}`)}</CardTitle></CardHeader><CardContent className="space-y-2">{Object.entries(groups[index] ?? {}).map(([itemStatus, count]) => <div key={itemStatus} className="flex justify-between text-sm"><span>{itemStatus.replaceAll("_", " ")}</span><strong>{format.number(count)}</strong></div>)}{!data && <p className="text-sm text-slate-400">{t("loading")}</p>}</CardContent></Card>)}</div>
    <Card><CardHeader><CardTitle className="text-base">{t("overview.upcoming")}</CardTitle></CardHeader><CardContent className="space-y-3">{data?.upcomingInterviews.map((item) => <Link key={item.id} href={`/recruitment/interviews?selected=${item.id}`} className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3 hover:bg-slate-50"><span><strong>{item.candidateName}</strong><span className="ml-2 text-sm text-slate-500">{item.jobTitle}</span></span><span className="text-sm">{item.scheduledStartAt ? format.dateTime(new Date(item.scheduledStartAt), { dateStyle: "medium", timeStyle: "short" }) : t("notScheduled")}</span></Link>)}{data?.upcomingInterviews.length === 0 && <p className="text-sm text-slate-500">{t("overview.noUpcoming")}</p>}</CardContent></Card>
  </div>;
}

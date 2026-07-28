"use client";


import { useCallback, useEffect, useMemo, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useApiClient } from "@/hooks/useApiClient";
import { getAnalytics, getRecruitmentAnalytics, type AnalyticsDays, type AnalyticsResponse, type AnalyticsScope, type RecruitmentAnalyticsResponse } from "@/lib/usage-api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { AlertCircle, BarChart3, CheckCircle, Clock, MessageSquare, TrendingDown, TrendingUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getBillingAccount } from "@/lib/billing-api";
import { useRouter } from "@/i18n/navigation";

const scopeOptions: Array<{ value: AnalyticsScope; labelKey: "customer" | "employee" | "all" }> = [
  { value: "CUSTOMER", labelKey: "customer" }, { value: "EMPLOYEE", labelKey: "employee" }, { value: "ALL", labelKey: "all" },
];

export default function AnalyticsPage() {
  const t = useTranslations("Analytics");
  const format = useFormatter();
  const { request } = useApiClient();
  const router = useRouter();
  const [scope, setScope] = useState<AnalyticsScope>("CUSTOMER");
  const [range, setRange] = useState<AnalyticsDays>(30);
  const [analytics, setAnalytics] = useState<AnalyticsResponse | null>(null);
  const [recruitmentAnalytics,setRecruitmentAnalytics]=useState<RecruitmentAnalyticsResponse|null>(null);
  const [product,setProduct]=useState<"conversations"|"recruitment">("conversations");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [upgradeRequired, setUpgradeRequired] = useState(false);

  const loadAnalytics = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError(null);
    try {
      const account = await getBillingAccount(request);
      if (!account.features.advancedAnalytics) {
        setUpgradeRequired(true);
        setAnalytics(null);
        return;
      }
      setUpgradeRequired(false);
      if(product==="recruitment")setRecruitmentAnalytics(await getRecruitmentAnalytics(request,range,signal));
      else setAnalytics(await getAnalytics(request, scope, range, signal));
    }
    catch (cause) { if (!(cause instanceof DOMException && cause.name === "AbortError")) setError(cause instanceof Error ? cause.message : t("loadError")); }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [product,range, request, scope, t]);

  const formatDuration = useCallback((milliseconds: number) => milliseconds < 1000
    ? `${format.number(Math.round(milliseconds))}ms`
    : `${format.number(milliseconds / 1000, { maximumFractionDigits: 1 })}s`, [format]);
  const trendLabel = useCallback((value: number, suffix = "%") => t("vsLastPeriod", {
    value: format.number(value, { maximumFractionDigits: 1, signDisplay: "exceptZero" }), suffix,
  }), [format, t]);

  useEffect(() => {
    const controller = new AbortController();
    const task = window.setTimeout(() => void loadAnalytics(controller.signal), 0);
    return () => { window.clearTimeout(task); controller.abort(); };
  }, [loadAnalytics]);

  const metricCards = useMemo(() => analytics ? [
    { label: t("metrics.conversations"), value: format.number(analytics.sessions.value), icon: MessageSquare, trend: analytics.sessions.percentageChange, suffix: "%", color: "text-indigo-600", bg: "bg-indigo-50" },
    { label: t("metrics.responseTime"), value: formatDuration(analytics.averageAssistantResponseTime.milliseconds), icon: Clock, trend: analytics.averageAssistantResponseTime.percentageChange, suffix: "%", color: "text-violet-600", bg: "bg-violet-50" },
    { label: t("metrics.closedRate"), value: `${format.number(analytics.closedSessionRate.percentage, { maximumFractionDigits: 1 })}%`, icon: CheckCircle, trend: analytics.closedSessionRate.percentagePointChange, suffix: t("percentagePoints"), color: "text-emerald-600", bg: "bg-emerald-50" },
    scope === "CUSTOMER"
      ? { label: t("metrics.resolvedRate"), value: `${format.number(analytics.resolvedTicketRate?.percentage ?? 0, { maximumFractionDigits: 1 })}%`, icon: CheckCircle, trend: analytics.resolvedTicketRate?.percentagePointChange ?? 0, suffix: t("percentagePoints"), color: "text-amber-600", bg: "bg-amber-50" }
      : { label: t("metrics.userMessages"), value: format.number(analytics.userMessages.value), icon: BarChart3, trend: analytics.userMessages.percentageChange, suffix: "%", color: "text-amber-600", bg: "bg-amber-50" },
  ] : [], [analytics, format, formatDuration, scope, t]);

  const maxCount = Math.max(0, ...(analytics?.dailyMessageVolume.map((d) => d.count) ?? []));
  const maxQuestion = Math.max(0, ...(analytics?.popularQuestions.map((q) => q.count) ?? []));
  const noActivity = !loading && analytics?.sessions.value === 0 && analytics.userMessages.value === 0;

  if (!loading && upgradeRequired) {
    return <Card className="mx-auto max-w-xl"><CardHeader><CardTitle>{t("proTitle")}</CardTitle></CardHeader><CardContent className="space-y-4"><p className="text-sm text-slate-600">{t("proDescription")}</p><Button onClick={() => router.push("/settings?tab=quota")}>{t("viewPlans")}</Button></CardContent></Card>;
  }

  if(product==="recruitment")return <div className="space-y-6"><ProductTabs product={product} setProduct={setProduct}/>{error&&<div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>}<div className="flex justify-end"><RangeSelect range={range} setRange={setRange}/></div><RecruitmentAnalyticsPanel value={recruitmentAnalytics} loading={loading} format={format}/></div>;

  return (
    <div className="space-y-6">
      <ProductTabs product={product} setProduct={setProduct}/><div className="flex flex-wrap items-center justify-end gap-3"><div className="flex rounded-lg border bg-white p-1">{scopeOptions.map(option => <button key={option.value} type="button" onClick={() => setScope(option.value)} className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${scope === option.value ? "bg-indigo-600 text-white" : "text-slate-500 hover:text-slate-800"}`}>{t(`scope.${option.labelKey}`)}</button>)}</div></div>

      {error && <div role="alert" className="flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"><span className="flex items-center gap-2"><AlertCircle className="size-4" />{error}</span><Button size="sm" variant="outline" onClick={() => void loadAnalytics()}>{t("retry")}</Button></div>}
      {noActivity && <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">{t("noActivity")}</div>}

      {/* Metric cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {loading ? Array.from({ length: 4 }).map((_, index) => <Card key={index}><CardContent className="p-5"><Skeleton className="mb-4 h-4 w-32" /><Skeleton className="mb-2 h-8 w-20" /><Skeleton className="h-3 w-36" /></CardContent></Card>) : metricCards.map(({ label, value, icon: Icon, trend, suffix, color, bg }) => (
          <Card key={label}>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-3">
                <span className="text-sm text-slate-500">{label}</span>
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${bg}`}>
                  <Icon className={`w-4 h-4 ${color}`} />
                </div>
              </div>
              <div className="text-2xl font-bold text-slate-800 mb-1">{value}</div>
              <div className={`flex items-center gap-1 text-xs ${trend > 0 ? "text-green-600" : trend < 0 ? "text-red-500" : "text-slate-400"}`}>
                {trend >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                {trendLabel(trend, suffix)}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Bar chart */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base">{t("messageVolume")}</CardTitle>
          <Select value={String(range)} onValueChange={(v) => setRange(Number(v) as AnalyticsDays)}>
            <SelectTrigger className="w-36 h-8 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="7">{t("lastDays", { count: 7 })}</SelectItem>
              <SelectItem value="30">{t("lastDays", { count: 30 })}</SelectItem>
              <SelectItem value="90">{t("lastDays", { count: 90 })}</SelectItem>
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-1 h-40">
            {(analytics?.dailyMessageVolume ?? []).map((d) => {
              const heightPct = maxCount === 0 ? 0 : (d.count / maxCount) * 100;
              const chartDate = new Date(`${d.date}T00:00:00Z`);
              const dateLabel = format.dateTime(chartDate, { month: "short", day: "numeric", timeZone: "UTC" });
              return (
                <div key={d.date} className="relative group flex-1 flex flex-col items-center justify-end h-full">
                  {/* Tooltip */}
                  <div className="absolute bottom-full mb-1 hidden group-hover:block z-10">
                    <div className="bg-slate-800 text-white text-xs rounded px-2 py-1 whitespace-nowrap">
                      {t("chartMessages", { date: dateLabel, count: d.count })}
                    </div>
                  </div>
                  <div
                    className="w-full bg-indigo-500 hover:bg-indigo-600 rounded-t transition-colors cursor-pointer"
                    style={{ height: `${heightPct}%`, minHeight: d.count > 0 ? "4px" : "0" }}
                  />
                </div>
              );
            })}
          </div>{!loading && maxCount === 0 && <p className="mt-3 text-center text-sm text-slate-400">{t("noMessages")}</p>}
          <div className="flex justify-between text-xs text-slate-400 mt-2 px-0.5">
            <span>
              {analytics?.dailyMessageVolume[0] ? format.dateTime(new Date(`${analytics.dailyMessageVolume[0].date}T00:00:00Z`), { month: "short", day: "numeric", timeZone: "UTC" }) : ""}
            </span>
            <span>
              {analytics?.dailyMessageVolume.at(-1) ? format.dateTime(new Date(`${analytics.dailyMessageVolume.at(-1)!.date}T00:00:00Z`), { month: "short", day: "numeric", timeZone: "UTC" }) : ""}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Popular questions */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t("popularQuestions")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {loading ? Array.from({ length: 5 }).map((_, index) => <Skeleton key={index} className="h-10 w-full" />) : (analytics?.popularQuestions ?? []).map(({ question, count }) => {
            const widthPct = maxQuestion === 0 ? 0 : (count / maxQuestion) * 100;
            return (
              <div key={question} className="relative rounded-lg overflow-hidden">
                <div
                  className="absolute inset-y-0 left-0 bg-indigo-50 rounded-lg"
                  style={{ width: `${widthPct}%` }}
                />
                <div className="relative flex items-center justify-between px-3 py-2.5">
                  <span className="text-sm text-slate-700 truncate pr-4">{question}</span>
                  <span className="text-sm font-semibold text-indigo-700 shrink-0">{count}</span>
                </div>
              </div>
            );
          })}{!loading && (analytics?.popularQuestions.length ?? 0) === 0 && <div className="py-10 text-center text-sm text-slate-400">{t("noPopularQuestions")}</div>}
        </CardContent>
      </Card>
    </div>
  );
}

function ProductTabs({product,setProduct}:{product:"conversations"|"recruitment";setProduct:(value:"conversations"|"recruitment")=>void}){const t=useTranslations("Analytics");return <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-semibold text-slate-800">{t("title")}</h2><div className="flex rounded-lg border bg-white p-1">{(["conversations","recruitment"] as const).map(value=><button key={value} type="button" onClick={()=>setProduct(value)} className={`rounded-md px-3 py-1.5 text-sm font-medium ${product===value?"bg-indigo-600 text-white":"text-slate-500"}`}>{t(`product.${value}`)}</button>)}</div></div>}
function RangeSelect({range,setRange}:{range:AnalyticsDays;setRange:(value:AnalyticsDays)=>void}){const t=useTranslations("Analytics");return <Select value={String(range)} onValueChange={v=>setRange(Number(v) as AnalyticsDays)}><SelectTrigger className="w-36"><SelectValue/></SelectTrigger><SelectContent>{[7,30,90].map(value=><SelectItem key={value} value={String(value)}>{t("lastDays",{count:value})}</SelectItem>)}</SelectContent></Select>}
function RecruitmentAnalyticsPanel({value,loading,format}:{value:RecruitmentAnalyticsResponse|null;loading:boolean;format:ReturnType<typeof useFormatter>}){const t=useTranslations("Analytics");const metrics=value?[value.jobsPublished,value.verifiedApplicationsSubmitted,value.completedInterviews,value.unsuccessfulInterviews]:[];const labels=["jobsPublished","applicationsSubmitted","interviewsCompleted","interviewsUnsuccessful"] as const;const distributions=value?[["jobStatuses",value.jobStatusDistribution],["applicationStatuses",value.applicationStatusDistribution],["interviewStatuses",value.interviewStatusDistribution]] as const:[];return <div className="space-y-5" aria-live="polite"><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{labels.map((label,index)=><Card key={label}><CardContent className="p-5"><p className="text-sm text-slate-500">{t(`recruitment.${label}`)}</p><p className="mt-2 text-2xl font-bold">{loading?"…":format.number(metrics[index]?.value??0)}</p><p className="text-xs text-slate-500">{t("vsLastPeriod",{value:format.number(metrics[index]?.percentageChange??0,{maximumFractionDigits:1,signDisplay:"exceptZero"}),suffix:"%"})}</p></CardContent></Card>)}</div><div className="grid gap-4 lg:grid-cols-3">{distributions.map(([label,distribution])=><Card key={label}><CardHeader><CardTitle className="text-base">{t(`recruitment.${label}`)}</CardTitle></CardHeader><CardContent className="space-y-2">{Object.entries(distribution).map(([itemStatus,count])=><div key={itemStatus} className="flex justify-between text-sm"><span>{itemStatus.replaceAll("_"," ")}</span><strong>{format.number(count)}</strong></div>)}</CardContent></Card>)}</div></div>}

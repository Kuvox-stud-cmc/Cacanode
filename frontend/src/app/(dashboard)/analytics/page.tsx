"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useApiClient } from "@/hooks/useApiClient";
import { getAnalytics, type AnalyticsDays, type AnalyticsResponse, type AnalyticsScope } from "@/lib/usage-api";
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

const scopeOptions: Array<{ value: AnalyticsScope; label: string }> = [
  { value: "CUSTOMER", label: "Customer" }, { value: "EMPLOYEE", label: "Employee" }, { value: "ALL", label: "All" },
];

function formatDuration(milliseconds: number) {
  if (milliseconds < 1000) return `${Math.round(milliseconds)}ms`;
  return `${(milliseconds / 1000).toFixed(1)}s`;
}

function trendLabel(value: number, suffix = "%") {
  return `${value > 0 ? "+" : ""}${value.toFixed(1)}${suffix} vs last period`;
}

export default function AnalyticsPage() {
  const { request } = useApiClient();
  const [scope, setScope] = useState<AnalyticsScope>("CUSTOMER");
  const [range, setRange] = useState<AnalyticsDays>(30);
  const [analytics, setAnalytics] = useState<AnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAnalytics = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError(null);
    try { setAnalytics(await getAnalytics(request, scope, range, signal)); }
    catch (cause) { if (!(cause instanceof DOMException && cause.name === "AbortError")) setError(cause instanceof Error ? cause.message : "Unable to load analytics"); }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [range, request, scope]);

  useEffect(() => {
    const controller = new AbortController();
    const task = window.setTimeout(() => void loadAnalytics(controller.signal), 0);
    return () => { window.clearTimeout(task); controller.abort(); };
  }, [loadAnalytics]);

  const metricCards = useMemo(() => analytics ? [
    { label: "Conversations", value: analytics.sessions.value.toLocaleString(), icon: MessageSquare, trend: analytics.sessions.percentageChange, suffix: "%", color: "text-indigo-600", bg: "bg-indigo-50" },
    { label: "Avg Response Time", value: formatDuration(analytics.averageAssistantResponseTime.milliseconds), icon: Clock, trend: analytics.averageAssistantResponseTime.percentageChange, suffix: "%", color: "text-violet-600", bg: "bg-violet-50" },
    { label: "Closed Session Rate", value: `${analytics.closedSessionRate.percentage.toFixed(1)}%`, icon: CheckCircle, trend: analytics.closedSessionRate.percentagePointChange, suffix: " pp", color: "text-emerald-600", bg: "bg-emerald-50" },
    scope === "CUSTOMER"
      ? { label: "Resolved Ticket Rate", value: `${(analytics.resolvedTicketRate?.percentage ?? 0).toFixed(1)}%`, icon: CheckCircle, trend: analytics.resolvedTicketRate?.percentagePointChange ?? 0, suffix: " pp", color: "text-amber-600", bg: "bg-amber-50" }
      : { label: "User Messages", value: analytics.userMessages.value.toLocaleString(), icon: BarChart3, trend: analytics.userMessages.percentageChange, suffix: "%", color: "text-amber-600", bg: "bg-amber-50" },
  ] : [], [analytics, scope]);

  const maxCount = Math.max(0, ...(analytics?.dailyMessageVolume.map((d) => d.count) ?? []));
  const maxQuestion = Math.max(0, ...(analytics?.popularQuestions.map((q) => q.count) ?? []));
  const noActivity = !loading && analytics?.sessions.value === 0 && analytics.userMessages.value === 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-semibold text-slate-800">Analytics</h2><div className="flex rounded-lg border bg-white p-1">{scopeOptions.map(option => <button key={option.value} type="button" onClick={() => setScope(option.value)} className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${scope === option.value ? "bg-indigo-600 text-white" : "text-slate-500 hover:text-slate-800"}`}>{option.label}</button>)}</div></div>

      {error && <div role="alert" className="flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"><span className="flex items-center gap-2"><AlertCircle className="size-4" />{error}</span><Button size="sm" variant="outline" onClick={() => void loadAnalytics()}>Retry</Button></div>}
      {noActivity && <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">No activity was recorded for this scope during the selected period.</div>}

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
          <CardTitle className="text-base">Message Volume</CardTitle>
          <Select value={String(range)} onValueChange={(v) => setRange(Number(v) as AnalyticsDays)}>
            <SelectTrigger className="w-36 h-8 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="7">Last 7 days</SelectItem>
              <SelectItem value="30">Last 30 days</SelectItem>
              <SelectItem value="90">Last 90 days</SelectItem>
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-1 h-40">
            {(analytics?.dailyMessageVolume ?? []).map((d) => {
              const heightPct = maxCount === 0 ? 0 : (d.count / maxCount) * 100;
              const chartDate = new Date(`${d.date}T00:00:00Z`);
              const month = chartDate.toLocaleString("default", { month: "short", timeZone: "UTC" });
              const day = chartDate.getUTCDate();
              return (
                <div key={d.date} className="relative group flex-1 flex flex-col items-center justify-end h-full">
                  {/* Tooltip */}
                  <div className="absolute bottom-full mb-1 hidden group-hover:block z-10">
                    <div className="bg-slate-800 text-white text-xs rounded px-2 py-1 whitespace-nowrap">
                      {month} {day}: {d.count} msgs
                    </div>
                  </div>
                  <div
                    className="w-full bg-indigo-500 hover:bg-indigo-600 rounded-t transition-colors cursor-pointer"
                    style={{ height: `${heightPct}%`, minHeight: d.count > 0 ? "4px" : "0" }}
                  />
                </div>
              );
            })}
          </div>{!loading && maxCount === 0 && <p className="mt-3 text-center text-sm text-slate-400">No messages in this period.</p>}
          <div className="flex justify-between text-xs text-slate-400 mt-2 px-0.5">
            <span>
              {analytics?.dailyMessageVolume[0] ? new Date(`${analytics.dailyMessageVolume[0].date}T00:00:00Z`).toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" }) : ""}
            </span>
            <span>
              {analytics?.dailyMessageVolume.at(-1) ? new Date(`${analytics.dailyMessageVolume.at(-1)!.date}T00:00:00Z`).toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" }) : ""}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Popular questions */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Popular Questions</CardTitle>
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
          })}{!loading && (analytics?.popularQuestions.length ?? 0) === 0 && <div className="py-10 text-center text-sm text-slate-400">No popular questions yet.</div>}
        </CardContent>
      </Card>
    </div>
  );
}

"use client"

import { useCallback, useEffect, useState } from "react"
import { AlertTriangle, Loader2, RefreshCw } from "lucide-react"
import { useFormatter, useTranslations } from "next-intl"
import { useSearchParams } from "next/navigation"
import { useRouter } from "@/i18n/navigation"
import { useApiClient } from "@/hooks/useApiClient"
import { Button } from "@/components/ui/button"
import { getPlatformOverview, type DailyTrend, type PlatformOverview } from "@/lib/platform-api"

export default function PlatformOverviewPage() {
  const t = useTranslations("Platform.overview")
  const format = useFormatter()
  const params = useSearchParams()
  const router = useRouter()
  const { request } = useApiClient()
  const requested = Number(params.get("days") ?? 30)
  const days = [7, 30, 90].includes(requested) ? requested : 30
  const [data, setData] = useState<PlatformOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const load = useCallback(async () => {
    setLoading(true); setError("")
    try { setData(await getPlatformOverview(request, days)) }
    catch (cause) { setError(cause instanceof Error ? cause.message : t("loadError")) }
    finally { setLoading(false) }
  }, [days, request, t])
  useEffect(() => { const id = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(id) }, [load])
  const number = (value: number) => format.number(value, { notation: value > 999999 ? "compact" : "standard", maximumFractionDigits: 1 })
  const cards = data ? ["activeUsers", "documents", "storageBytes", "conversations", "openTickets", "jobs", "verifiedApplications", "completedInterviews", "unsuccessfulInterviews"].map(key => ({ key, value: data[key as keyof PlatformOverview] as { value: number; percentageChange: number } })) : []
  return <div className="mx-auto max-w-7xl space-y-6">
    <header className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold text-slate-950">{t("title")}</h1><p className="mt-1 text-sm text-slate-600">{t("description")}</p></div><div className="flex items-center gap-2"><div className="rounded-lg border bg-white p-1" aria-label={t("rangeLabel")}>{[7,30,90].map(value => <button key={value} type="button" onClick={() => router.replace(`/platform?days=${value}`)} className={`rounded-md px-3 py-1.5 text-sm ${days === value ? "bg-indigo-600 text-white" : "text-slate-600"}`}>{value}d</button>)}</div><Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className="size-4" />{t("refresh")}</Button></div></header>
    {data?.partial && <div role="status" className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"><AlertTriangle className="mt-0.5 size-4 shrink-0" />{t("partial")}</div>}
    {error && <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">{error}</div>}
    {loading && !data ? <div className="grid h-64 place-items-center"><Loader2 className="size-8 animate-spin text-indigo-600" /></div> : !data ? null : <>
      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{cards.map(({key,value}) => <article key={key} className="rounded-xl border bg-white p-5 shadow-sm"><p className="text-sm text-slate-500">{t(`metrics.${key}` as never)}</p><p className="mt-2 text-2xl font-bold text-slate-950">{key === "storageBytes" ? format.number(value.value / (1024*1024), { maximumFractionDigits: 1 }) + " MB" : number(value.value)}</p><p className={`mt-1 text-xs ${value.percentageChange > 0 ? "text-emerald-700" : value.percentageChange < 0 ? "text-red-700" : "text-slate-400"}`}>{value.percentageChange > 0 ? "+" : ""}{format.number(value.percentageChange,{maximumFractionDigits:1})}%</p></article>)}</section>
      <section className="grid gap-4 lg:grid-cols-2"><Distribution title={t("statusDistribution")} values={data.tenantStatuses} /><Distribution title={t("planDistribution")} values={data.tenantPlans} /></section>
      <section className="rounded-xl border bg-white p-5"><h2 className="font-semibold text-slate-950">{t("trends")}</h2><div className="mt-5 grid gap-5 md:grid-cols-2 xl:grid-cols-5">{(["tenants","jobs","verifiedApplications","completedInterviews","unsuccessfulInterviews"] as const).map(key => <Sparkline key={key} title={t(`trend.${key}` as never)} values={data.trends} field={key} />)}</div></section>
      <section className="rounded-xl border bg-white p-5"><h2 className="font-semibold text-slate-950">{t("freshness")}</h2><dl className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{Object.entries(data.freshness.projections).map(([name,value]) => <div key={name} className="rounded-lg bg-slate-50 p-3"><dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{name}</dt><dd className="mt-1 text-sm text-slate-800">{value ? format.dateTime(new Date(value), { dateStyle: "medium", timeStyle: "short" }) : t("never")}</dd></div>)}</dl></section>
    </>}
  </div>
}

function Distribution({ title, values }: { title: string; values: Record<string, number> }) {
  const total = Object.values(values).reduce((sum, value) => sum + value, 0)
  return <section className="rounded-xl border bg-white p-5"><h2 className="font-semibold text-slate-950">{title}</h2>{total === 0 ? <p className="mt-5 text-sm text-slate-500">—</p> : <div className="mt-4 space-y-3">{Object.entries(values).map(([name,value]) => <div key={name}><div className="flex justify-between text-sm"><span>{name}</span><span>{value}</span></div><div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-indigo-500" style={{width:`${value/total*100}%`}} /></div></div>)}</div>}</section>
}

function Sparkline({ title, values, field }: { title: string; values: DailyTrend[]; field: keyof Omit<DailyTrend, "date"> }) {
  const width = 220, height = 72, maximum = Math.max(1, ...values.map(item => item[field]))
  const points = values.map((item,index) => `${values.length === 1 ? 0 : index/(values.length-1)*width},${height-item[field]/maximum*(height-8)-4}`).join(" ")
  return <figure><figcaption className="mb-2 text-sm text-slate-600">{title}</figcaption><svg role="img" aria-label={`${title}: ${values.map(item => `${item.date} ${item[field]}`).join(", ")}`} viewBox={`0 0 ${width} ${height}`} className="h-20 w-full"><polyline points={points} fill="none" stroke="currentColor" strokeWidth="3" className="text-indigo-600" vectorEffect="non-scaling-stroke" /></svg></figure>
}

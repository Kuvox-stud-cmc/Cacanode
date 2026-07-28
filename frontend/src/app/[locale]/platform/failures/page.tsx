"use client"

import { useCallback, useEffect, useState } from "react"
import { AlertTriangle, ExternalLink, Loader2, RefreshCw } from "lucide-react"
import { useFormatter, useTranslations } from "next-intl"
import { useSearchParams } from "next/navigation"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useApiClient } from "@/hooks/useApiClient"
import { Link, useRouter } from "@/i18n/navigation"
import {
  getFailures,
  getFailureSummary,
  type Failure,
  type FailurePage,
  type FailureSeverity,
  type FailureSource,
  type FailureState,
  type FailureSummary,
} from "@/lib/platform-api"

const sources: FailureSource[] = ["MODULE_EVENTS", "DOCUMENT_INGESTION", "WEBHOOKS", "BILLING", "CV_ANALYSIS", "INTERVIEW_TRANSPORT", "CANDIDATE_EMAIL", "RECORDING", "PRIVACY_ERASURE"]
const states: FailureState[] = ["RETRYING", "FAILED", "DEAD", "REVIEW", "STALLED"]
const severities: FailureSeverity[] = ["WARNING", "ERROR", "CRITICAL"]
const sorts = ["lastSeenAt", "firstSeenAt", "attempts", "severity", "state"] as const

export default function PlatformFailuresPage() {
  const t = useTranslations("Platform.failures")
  const format = useFormatter()
  const params = useSearchParams()
  const router = useRouter()
  const { request } = useApiClient()
  const requestedSource = params.get("source") as FailureSource
  const source = sources.includes(requestedSource) ? requestedSource : sources[0]
  const tenantId = params.get("tenantId") ?? ""
  const state = (params.get("state") ?? "") as FailureState | ""
  const severity = (params.get("severity") ?? "") as FailureSeverity | ""
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0)
  const requestedSort = params.get("sort") as typeof sorts[number]
  const sort = sorts.includes(requestedSort) ? requestedSort : "lastSeenAt"
  const direction = params.get("direction") === "asc" ? "asc" : "desc"
  const [tenant, setTenant] = useState(tenantId)
  const [summary, setSummary] = useState<FailureSummary | null>(null)
  const [data, setData] = useState<FailurePage | null>(null)
  const [summaryLoading, setSummaryLoading] = useState(true)
  const [listLoading, setListLoading] = useState(true)
  const [summaryError, setSummaryError] = useState(false)
  const [listError, setListError] = useState(false)

  const update = useCallback((values: Record<string, string | number>) => {
    const next = new URLSearchParams(params.toString())
    Object.entries(values).forEach(([key, value]) => value === "" ? next.delete(key) : next.set(key, String(value)))
    router.replace(`/platform/failures?${next}`)
  }, [params, router])
  const loadSummary = useCallback(async () => {
    setSummaryLoading(true)
    try {
      setSummary(await getFailureSummary(request, tenantId || undefined))
      setSummaryError(false)
    } catch {
      setSummaryError(true)
    } finally {
      setSummaryLoading(false)
    }
  }, [request, tenantId])
  const loadList = useCallback(async () => {
    setListLoading(true)
    try {
      setData(await getFailures(request, source, { tenantId, state, severity, page, size: 20, sort, direction }))
      setListError(false)
    } catch {
      setListError(true)
    } finally {
      setListLoading(false)
    }
  }, [direction, page, request, severity, sort, source, state, tenantId])
  const refreshAll = useCallback(async () => {
    await Promise.allSettled([loadSummary(), loadList()])
  }, [loadList, loadSummary])

  useEffect(() => { const id = window.setTimeout(() => void loadList(), 0); return () => window.clearTimeout(id) }, [loadList])
  useEffect(() => {
    let interval: number | undefined
    const visible = () => {
      if (interval !== undefined) window.clearInterval(interval)
      interval = undefined
      if (document.visibilityState === "visible") {
        void loadSummary()
        interval = window.setInterval(() => void loadSummary(), 30_000)
      }
    }
    visible()
    document.addEventListener("visibilitychange", visible)
    return () => {
      if (interval !== undefined) window.clearInterval(interval)
      document.removeEventListener("visibilitychange", visible)
    }
  }, [loadSummary])

  const current = summary?.sources.find(item => item.source === source)
  const pages = Math.max(1, Math.ceil((data?.total ?? 0) / 20))
  return <div className="mx-auto max-w-7xl space-y-5">
    <header className="flex flex-wrap items-center justify-between gap-3">
      <div><h1 className="text-2xl font-bold">{t("title")}</h1><p className="mt-1 text-sm text-slate-600">{t("description")}</p></div>
      <Button variant="outline" onClick={() => void refreshAll()} disabled={summaryLoading || listLoading} aria-label={t("refreshAria")}><RefreshCw className="size-4" />{t("refresh")}</Button>
    </header>

    {summaryError && <RetainedWarning retained={summary !== null} text={t(summary ? "summaryRetained" : "summaryUnavailable")} />}
    {summary?.partial && <PartialWarning warnings={summary.warnings} label={t("summaryPartial")} t={t} />}
    {summaryLoading && !summary ? <div className="grid h-28 place-items-center" aria-label={t("summaryLoading")}><Loader2 className="size-6 animate-spin text-indigo-600" /></div> :
      <section aria-label={t("summaryAria")} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        {summary?.sources.map(item => <button key={item.source} type="button" onClick={() => update({ source: item.source, page: 0 })} aria-pressed={source === item.source} className={`rounded-xl border p-4 text-left transition ${source === item.source ? "border-indigo-500 bg-indigo-50 ring-1 ring-indigo-200" : "bg-white hover:border-slate-300"}`}>
          <p className="text-xs font-medium text-slate-600">{t(`sources.${item.source}` as never)}</p><p className="mt-2 text-2xl font-bold">{format.number(item.total)}</p>
          <div className="mt-3 flex flex-wrap gap-1.5">{states.filter(value => item.states[value]).map(value => <CountPill key={value} label={t(`states.${value}` as never)} count={item.states[value] ?? 0} />)}</div>
          <div className="mt-2 flex flex-wrap gap-1.5">{severities.filter(value => item.severities[value]).map(value => <CountPill key={value} label={t(`severities.${value}` as never)} count={item.severities[value] ?? 0} />)}</div>
        </button>)}
      </section>}

    <section className="overflow-hidden rounded-xl border bg-white">
      <form className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-6" onSubmit={event => { event.preventDefault(); update({ tenantId: tenant.trim(), page: 0 }) }}>
        <Filter label={t("filters.source")}><select aria-label={t("filters.source")} value={source} onChange={event => update({ source: event.target.value, page: 0 })} className="h-9 w-full rounded-md border px-3 text-sm">{sources.map(item => <option key={item} value={item}>{t(`sources.${item}` as never)}</option>)}</select></Filter>
        <Filter label={t("filters.tenant")}><Input aria-label={t("filters.tenant")} value={tenant} onChange={event => setTenant(event.target.value)} placeholder={t("tenantFilter")} /></Filter>
        <Filter label={t("filters.state")}><select aria-label={t("filters.state")} value={state} onChange={event => update({ state: event.target.value, page: 0 })} className="h-9 w-full rounded-md border px-3 text-sm"><option value="">{t("allStates")}</option>{states.map(item => <option key={item} value={item}>{t(`states.${item}` as never)}</option>)}</select></Filter>
        <Filter label={t("filters.severity")}><select aria-label={t("filters.severity")} value={severity} onChange={event => update({ severity: event.target.value, page: 0 })} className="h-9 w-full rounded-md border px-3 text-sm"><option value="">{t("allSeverities")}</option>{severities.map(item => <option key={item} value={item}>{t(`severities.${item}` as never)}</option>)}</select></Filter>
        <Filter label={t("filters.sort")}><select aria-label={t("filters.sort")} value={sort} onChange={event => update({ sort: event.target.value, page: 0 })} className="h-9 w-full rounded-md border px-3 text-sm">{sorts.map(item => <option key={item} value={item}>{t(`sort.${item}` as never)}</option>)}</select></Filter>
        <div className="flex items-end gap-2"><select aria-label={t("filters.direction")} value={direction} onChange={event => update({ direction: event.target.value, page: 0 })} className="h-9 min-w-0 flex-1 rounded-md border px-3 text-sm"><option value="desc">{t("descending")}</option><option value="asc">{t("ascending")}</option></select><Button variant="outline" type="submit">{t("apply")}</Button></div>
      </form>
      {current && <div className="flex flex-wrap gap-2 border-b bg-slate-50 px-4 py-3 text-sm"><strong>{t("sourceTotal", { count: current.total })}</strong>{states.map(value => <CountPill key={value} label={t(`states.${value}` as never)} count={current.states[value] ?? 0} />)}{severities.map(value => <CountPill key={value} label={t(`severities.${value}` as never)} count={current.severities[value] ?? 0} />)}</div>}
      {listError && <RetainedWarning retained={data !== null} text={t(data ? "listRetained" : "listUnavailable")} />}
      {data?.partial && <PartialWarning warnings={data.warnings} label={t("listPartial")} t={t} />}
      {listLoading && !data ? <div className="grid h-56 place-items-center" aria-label={t("listLoading")}><Loader2 className="size-7 animate-spin text-indigo-600" /></div> : (data?.items.length ?? 0) === 0 ? <p className="p-14 text-center text-slate-500">{t("empty")}</p> : <FailureResults items={data?.items ?? []} t={t} format={format} />}
      <footer className="flex items-center justify-between border-t p-4 text-sm"><span>{t("total", { count: data?.total ?? 0 })}</span><div className="flex items-center gap-2"><Button size="sm" variant="outline" disabled={page === 0 || listLoading} onClick={() => update({ page: page - 1 })}>{t("previous")}</Button><span aria-label={t("pageAria", { page: page + 1, pages })}>{page + 1}/{pages}</span><Button size="sm" variant="outline" disabled={page + 1 >= pages || listLoading} onClick={() => update({ page: page + 1 })}>{t("next")}</Button></div></footer>
    </section>
  </div>
}

function FailureResults({ items, t, format }: { items: Failure[]; t: ReturnType<typeof useTranslations>; format: ReturnType<typeof useFormatter> }) {
  return <><div className="hidden overflow-x-auto md:block"><table className="w-full min-w-[1050px] text-left text-sm"><thead className="bg-slate-50 text-slate-500"><tr>{["failure", "resource", "tenant", "state", "severity", "attempts", "firstSeen", "lastSeen", "nextRetry"].map(key => <th key={key} className="px-4 py-3">{t(`columns.${key}` as never)}</th>)}</tr></thead><tbody>{items.map(item => <tr key={item.failureId} className="border-t align-top"><td className="px-4 py-3"><Code item={item} t={t} /></td><td className="px-4 py-3"><Resource item={item} t={t} /></td><td className="px-4 py-3"><Tenant item={item} /></td><td className="px-4 py-3"><Badge kind="state" value={item.state} t={t} /></td><td className="px-4 py-3"><Badge kind="severity" value={item.severity} t={t} /></td><td className="px-4 py-3">{format.number(item.attempts)}</td><td className="px-4 py-3">{date(item.firstSeenAt, format)}</td><td className="px-4 py-3">{date(item.lastSeenAt, format)}</td><td className="px-4 py-3">{date(item.nextRetryAt, format)}</td></tr>)}</tbody></table></div>
    <div className="divide-y md:hidden">{items.map(item => <article key={item.failureId} className="space-y-3 p-4"><Code item={item} t={t} /><div className="flex flex-wrap gap-2"><Badge kind="state" value={item.state} t={t} /><Badge kind="severity" value={item.severity} t={t} /></div><dl className="grid grid-cols-2 gap-3 text-sm"><Meta label={t("columns.resource")}><Resource item={item} t={t} /></Meta><Meta label={t("columns.tenant")}><Tenant item={item} /></Meta><Meta label={t("columns.attempts")}>{format.number(item.attempts)}</Meta><Meta label={t("columns.lastSeen")}>{date(item.lastSeenAt, format)}</Meta><Meta label={t("columns.firstSeen")}>{date(item.firstSeenAt, format)}</Meta><Meta label={t("columns.nextRetry")}>{date(item.nextRetryAt, format)}</Meta></dl></article>)}</div></>
}

function Code({ item, t }: { item: Failure; t: ReturnType<typeof useTranslations> }) { const translate = t as unknown as (key: string, values?: Record<string, string>) => string; const label = translate(`codes.${item.errorCode}`); return <div><strong>{label}</strong><p className="mt-1 max-w-sm text-xs text-slate-500">{translate("explanation", { code: label })}</p><p className="mt-1 break-all text-[11px] text-slate-400">{item.failureId}</p></div> }
function Resource({ item, t }: { item: Failure; t: ReturnType<typeof useTranslations> }) { const label = t(`resourceTypes.${item.resourceType}` as never); return item.resourceType === "JOB" && item.resourceId ? <Link href={`/platform/jobs/${item.resourceId}`} className="inline-flex items-center gap-1 text-indigo-700 hover:underline">{label}<ExternalLink className="size-3" /></Link> : <div><span>{label}</span>{item.resourceId && <p className="mt-1 break-all text-[11px] text-slate-400">{item.resourceId}</p>}</div> }
function Tenant({ item }: { item: Failure }) { return item.tenantId ? <Link href={`/platform/tenants/${item.tenantId}`} className="break-all text-indigo-700 hover:underline">{item.tenantId}</Link> : "—" }
function Badge({ kind, value, t }: { kind: "state" | "severity"; value: FailureState | FailureSeverity; t: ReturnType<typeof useTranslations> }) { const critical = value === "CRITICAL" || value === "DEAD"; const warning = value === "WARNING" || value === "RETRYING" || value === "REVIEW"; return <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${critical ? "bg-red-100 text-red-800" : warning ? "bg-amber-100 text-amber-800" : "bg-slate-100 text-slate-700"}`}>{t(`${kind === "state" ? "states" : "severities"}.${value}` as never)}</span> }
function CountPill({ label, count }: { label: string; count: number }) { return <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600">{label}: {count}</span> }
function Filter({ label, children }: { label: string; children: React.ReactNode }) { return <label className="space-y-1 text-xs font-medium text-slate-600"><span>{label}</span>{children}</label> }
function Meta({ label, children }: { label: string; children: React.ReactNode }) { return <div><dt className="text-xs text-slate-500">{label}</dt><dd className="mt-1 break-words">{children}</dd></div> }
function RetainedWarning({ text, retained }: { text: string; retained: boolean }) { return <p role="alert" className={`m-4 flex items-center gap-2 rounded p-3 text-sm ${retained ? "bg-amber-50 text-amber-900" : "bg-red-50 text-red-700"}`}><AlertTriangle className="size-4 shrink-0" />{text}</p> }
function PartialWarning({ warnings, label, t }: { warnings: { code: string; source: FailureSource | null }[]; label: string; t: ReturnType<typeof useTranslations> }) { return <div className="m-4 rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"><strong>{label}</strong><ul className="mt-1 list-disc pl-5">{warnings.map((warning, index) => <li key={`${warning.code}-${warning.source}-${index}`}>{t(`warnings.${warning.code}` as never)}{warning.source ? ` — ${t(`sources.${warning.source}` as never)}` : ""}</li>)}</ul></div> }
function date(value: string | null, format: ReturnType<typeof useFormatter>) { return value ? format.dateTime(new Date(value), { dateStyle: "medium", timeStyle: "short" }) : "—" }

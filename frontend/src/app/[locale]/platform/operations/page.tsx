"use client"

import { useCallback, useEffect, useState } from "react"
import { Activity, AlertTriangle, Boxes, Cpu, Database, HardDrive, Loader2, RefreshCw, Server } from "lucide-react"
import { useFormatter, useTranslations } from "next-intl"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useApiClient } from "@/hooks/useApiClient"
import { getPlatformHealth, getPlatformQueues, type DiagnosticComponentResult, type DiagnosticErrorCode, type DiagnosticQueueResult, type DiagnosticStatus, type PlatformHealthSnapshot, type PlatformQueuePage } from "@/lib/platform-api"

type Translator = (key: string, values?: Record<string, unknown>) => string

export default function PlatformOperationsPage() {
  const typedT = useTranslations("Platform.operations"), t = typedT as unknown as Translator
  const format = useFormatter(), { request } = useApiClient()
  const [health, setHealth] = useState<PlatformHealthSnapshot | null>(null)
  const [queues, setQueues] = useState<PlatformQueuePage | null>(null)
  const [healthError, setHealthError] = useState(false), [queueError, setQueueError] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    const [healthResult, queueResult] = await Promise.allSettled([
      getPlatformHealth(request), getPlatformQueues(request),
    ])
    if (healthResult.status === "fulfilled") { setHealth(healthResult.value); setHealthError(false) } else setHealthError(true)
    if (queueResult.status === "fulfilled") { setQueues(queueResult.value); setQueueError(false) } else setQueueError(true)
    setLoading(false)
  }, [request])

  useEffect(() => {
    let interval: number | undefined
    const stop = () => { if (interval !== undefined) { window.clearInterval(interval); interval = undefined } }
    const start = () => {
      stop()
      if (document.visibilityState === "visible") {
        void load()
        interval = window.setInterval(() => void load(), 15_000)
      }
    }
    const visibilityChanged = () => document.visibilityState === "visible" ? start() : stop()
    start()
    document.addEventListener("visibilitychange", visibilityChanged)
    return () => { stop(); document.removeEventListener("visibilitychange", visibilityChanged) }
  }, [load])

  const overall = health?.overallStatus ?? queues?.overallStatus
  const latestSnapshot = [health?.snapshotTime, queues?.snapshotTime].filter((value): value is string => Boolean(value)).sort().at(-1)
  return <div className="mx-auto max-w-7xl space-y-5">
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div><h1 className="text-2xl font-bold">{t("title")}</h1><p className="mt-1 text-sm text-slate-600">{t("description")}</p></div>
      <Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`}/>{t("refresh")}</Button>
    </header>

    {loading && !health && !queues ? <div className="grid h-64 place-items-center rounded-xl border bg-white"><Loader2 aria-label={t("loading")} className="size-8 animate-spin text-indigo-600"/></div> : <>
      <section className="flex flex-wrap items-center justify-between gap-4 rounded-xl border bg-white p-5" aria-labelledby="operations-summary">
        <div><p id="operations-summary" className="text-sm font-medium text-slate-500">{t("overall")}</p><div className="mt-2 flex items-center gap-3">{overall ? <StatusBadge status={overall} t={t}/> : <span>—</span>}<span className="text-sm text-slate-500">{latestSnapshot ? t("lastChecked", { time: date(latestSnapshot, format) }) : ""}</span></div></div>
        <p className="max-w-xl text-sm text-slate-600">{t("scopeNotice")}</p>
      </section>

      <section aria-labelledby="component-health" className="space-y-3">
        <div><h2 id="component-health" className="text-lg font-semibold">{t("components.title")}</h2><p className="text-sm text-slate-600">{t("components.description")}</p></div>
        {healthError && <RetainedError retained={Boolean(health)} text={t(health ? "healthRetainedError" : "healthLoadError")}/>} 
        {!health && !healthError ? null : health?.components.length === 0 ? <Empty text={t("components.empty")}/> : <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{health?.components.map(component => <ComponentCard key={component.component} component={component} t={t} format={format}/>)}</div>}
      </section>

      {health && <section aria-labelledby="runtime-resources" className="space-y-3"><div><h2 id="runtime-resources" className="text-lg font-semibold">{t("runtime.title")}</h2><p className="text-sm text-slate-600">{t("runtime.description")}</p></div><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard icon={Cpu} label={t("runtime.cpu")} scope={t("scopes.JVM_PROCESS")} value={health.runtimeMetrics.processCpuPercentage == null ? "—" : format.number(health.runtimeMetrics.processCpuPercentage, { maximumFractionDigits: 1 }) + "%"}/>
        <MetricCard icon={Server} label={t("runtime.processors")} scope={t("scopes.APPLICATION_CONTAINER")} value={format.number(health.runtimeMetrics.availableProcessors)}/>
        <MetricCard icon={Activity} label={t("runtime.uptime")} scope={t("scopes.JVM_PROCESS")} value={duration(health.runtimeMetrics.jvmUptimeMilliseconds, t)}/>
        <MetricCard icon={Boxes} label={t("runtime.heap")} scope={t("scopes.JVM_PROCESS")} value={`${bytes(health.runtimeMetrics.heapUsedBytes, format)} / ${bytes(health.runtimeMetrics.heapMaxBytes, format)}`} detail={t("runtime.committed", { value: bytes(health.runtimeMetrics.heapCommittedBytes, format) })}/>
        <MetricCard icon={HardDrive} label={t("runtime.filesystem")} scope={t("scopes.APPLICATION_CONTAINER")} value={t("runtime.usable", { usable: bytes(health.runtimeMetrics.filesystemUsableBytes, format), total: bytes(health.runtimeMetrics.filesystemTotalBytes, format) })}/>
      </div></section>}

      <section aria-labelledby="queue-health" className="space-y-3"><div><h2 id="queue-health" className="text-lg font-semibold">{t("queues.title")}</h2><p className="text-sm text-slate-600">{queues ? t("queues.thresholds", { warning: queues.warningDepth, critical: queues.criticalDepth }) : t("queues.description")}</p></div>
        {queueError && <RetainedError retained={Boolean(queues)} text={t(queues ? "queueRetainedError" : "queueLoadError")}/>} 
        {!queues && !queueError ? null : queues?.items.length === 0 ? <Empty text={t("queues.empty")}/> : queues && <QueueInventory queues={queues.items} t={t} format={format}/>} 
      </section>
    </>}
  </div>
}

function ComponentCard({ component, t, format }: { component: DiagnosticComponentResult; t: Translator; format: ReturnType<typeof useFormatter> }) {
  return <article className="rounded-xl border bg-white p-4"><div className="flex items-start justify-between gap-3"><div className="flex items-center gap-2"><Database className="size-4 text-slate-400"/><h3 className="font-semibold">{t(`componentNames.${component.component}`)}</h3></div><StatusBadge status={component.status} t={t}/></div><dl className="mt-4 grid grid-cols-2 gap-3 text-sm"><div><dt className="text-slate-500">{t("components.latency")}</dt><dd>{component.latencyMilliseconds == null ? "—" : t("milliseconds", { value: component.latencyMilliseconds })}</dd></div><div><dt className="text-slate-500">{t("components.checkedAt")}</dt><dd>{date(component.checkedAt, format)}</dd></div></dl>{component.errorCode && <p className="mt-3 rounded-md bg-slate-50 p-2 text-xs text-slate-600">{errorText(component.errorCode, t)}</p>}</article>
}

function QueueInventory({ queues, t, format }: { queues: DiagnosticQueueResult[]; t: Translator; format: ReturnType<typeof useFormatter> }) {
  return <div className="overflow-hidden rounded-xl border bg-white"><div className="hidden overflow-x-auto lg:block"><table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-slate-50 text-slate-500"><tr>{["queue", "domain", "kind", "ready", "consumers", "status", "checkedAt"].map(key => <th key={key} className="px-4 py-3">{t(`queues.columns.${key}`)}</th>)}</tr></thead><tbody>{queues.map(queue => <tr key={queue.queueId} className="border-t align-top"><td className="px-4 py-3 font-medium">{t(`queueNames.${queue.queueId}`)}{queue.errorCode && <p className="mt-1 max-w-sm text-xs font-normal text-slate-500">{errorText(queue.errorCode, t)}</p>}</td><td className="px-4 py-3">{t(`domains.${queue.domain}`)}</td><td className="px-4 py-3"><QueueKind dlq={queue.deadLetterQueue} t={t}/></td><td className="px-4 py-3">{queue.status === "DISABLED" ? "—" : format.number(queue.readyCount)}</td><td className="px-4 py-3">{queue.status === "DISABLED" ? "—" : format.number(queue.consumerCount)}</td><td className="px-4 py-3"><StatusBadge status={queue.status} t={t}/></td><td className="px-4 py-3 whitespace-nowrap">{date(queue.checkedAt, format)}</td></tr>)}</tbody></table></div><div className="grid gap-3 p-4 lg:hidden">{queues.map(queue => <article key={queue.queueId} className="rounded-lg border p-4"><div className="flex items-start justify-between gap-3"><div><h3 className="font-semibold">{t(`queueNames.${queue.queueId}`)}</h3><div className="mt-1 flex items-center gap-2 text-xs text-slate-500"><span>{t(`domains.${queue.domain}`)}</span><QueueKind dlq={queue.deadLetterQueue} t={t}/></div></div><StatusBadge status={queue.status} t={t}/></div><dl className="mt-4 grid grid-cols-2 gap-3 text-sm"><div><dt className="text-slate-500">{t("queues.columns.ready")}</dt><dd>{queue.status === "DISABLED" ? "—" : format.number(queue.readyCount)}</dd></div><div><dt className="text-slate-500">{t("queues.columns.consumers")}</dt><dd>{queue.status === "DISABLED" ? "—" : format.number(queue.consumerCount)}</dd></div></dl>{queue.errorCode && <p className="mt-3 text-xs text-slate-600">{errorText(queue.errorCode, t)}</p>}</article>)}</div></div>
}

function StatusBadge({ status, t }: { status: DiagnosticStatus; t: Translator }) { const color = status === "UP" ? "border-emerald-200 bg-emerald-50 text-emerald-800" : status === "DEGRADED" ? "border-amber-200 bg-amber-50 text-amber-800" : status === "DOWN" ? "border-red-200 bg-red-50 text-red-800" : "border-slate-200 bg-slate-50 text-slate-600"; return <Badge variant="outline" className={color} aria-label={t("statusAccessible", { status: t(`statuses.${status}`) })}>{t(`statuses.${status}`)}</Badge> }
function QueueKind({ dlq, t }: { dlq: boolean; t: Translator }) { return <Badge variant="outline" className={dlq ? "border-red-200 bg-red-50 text-red-700" : "border-blue-200 bg-blue-50 text-blue-700"}>{t(dlq ? "queues.dlq" : "queues.main")}</Badge> }
function MetricCard({ icon: Icon, label, scope, value, detail }: { icon: typeof Cpu; label: string; scope: string; value: string; detail?: string }) { return <article className="rounded-xl border bg-white p-4"><div className="flex items-center gap-2 text-slate-500"><Icon className="size-4"/><p className="text-sm">{label}</p></div><p className="mt-3 text-xl font-semibold">{value}</p>{detail && <p className="mt-1 text-xs text-slate-500">{detail}</p>}<p className="mt-3 text-xs font-medium uppercase tracking-wide text-indigo-600">{scope}</p></article> }
function RetainedError({ retained, text }: { retained: boolean; text: string }) { return <p role="alert" className={`flex items-center gap-2 rounded-lg p-3 text-sm ${retained ? "bg-amber-50 text-amber-800" : "bg-red-50 text-red-700"}`}><AlertTriangle className="size-4"/>{text}</p> }
function Empty({ text }: { text: string }) { return <p className="rounded-xl border bg-white p-12 text-center text-slate-500">{text}</p> }
function errorText(code: DiagnosticErrorCode, t: Translator) { return t(`errors.${code}`) }
function date(value: string, format: ReturnType<typeof useFormatter>) { return format.dateTime(new Date(value), { dateStyle: "medium", timeStyle: "short" }) }
function bytes(value: number, format: ReturnType<typeof useFormatter>) { if (value < 1024) return `${format.number(value)} B`; const units = ["KB", "MB", "GB", "TB"]; let amount = value, index = -1; do { amount /= 1024; index++ } while (amount >= 1024 && index < units.length - 1); return `${format.number(amount, { maximumFractionDigits: 1 })} ${units[index]}` }
function duration(milliseconds: number, t: Translator) { const minutes = Math.floor(milliseconds / 60_000), hours = Math.floor(minutes / 60), days = Math.floor(hours / 24); if (days > 0) return t("duration.days", { value: days }); if (hours > 0) return t("duration.hours", { value: hours }); return t("duration.minutes", { value: minutes }) }

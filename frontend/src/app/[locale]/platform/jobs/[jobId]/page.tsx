"use client"

import { useCallback, useEffect, useState } from "react"
import { ExternalLink, Loader2, RefreshCw } from "lucide-react"
import { useFormatter, useTranslations } from "next-intl"
import { useParams } from "next/navigation"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useApiClient } from "@/hooks/useApiClient"
import { Link } from "@/i18n/navigation"
import { getPlatformJob, type PlatformJobDetail } from "@/lib/platform-api"

export default function PlatformJobDetailPage(){
 const t=useTranslations("Platform.jobDetail"),format=useFormatter(),params=useParams<{jobId:string}>(),{request}=useApiClient()
 const [job,setJob]=useState<PlatformJobDetail|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState("")
 const load=useCallback(async()=>{setLoading(true);setError("");try{setJob(await getPlatformJob(request,params.jobId))}catch(cause){setError(cause instanceof Error?cause.message:t("loadError"))}finally{setLoading(false)}},[params.jobId,request,t])
 useEffect(()=>{const id=window.setTimeout(()=>void load(),0);return()=>window.clearTimeout(id)},[load])
 if(loading&&!job)return <div className="grid h-72 place-items-center"><Loader2 aria-label={t("loading")} className="size-8 animate-spin text-indigo-600"/></div>
 if(error&&!job)return <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">{error}</p>
 if(!job)return null
 const metadata=[["jobId",job.jobId],["publicId",job.publicId],["company",job.frozenCompanyName??t("neverPublished")],["department",job.department],["location",job.location],["language",job.language],["employmentType",job.employmentType],["workMode",job.workMode],["experienceLevel",job.experienceLevel]]
 const aggregates=[["totalApplications",job.totalApplications],["verifiedApplications",job.verifiedApplications],["totalInterviews",job.totalInterviews],["completedInterviews",job.completedInterviews],["unsuccessfulInterviews",job.unsuccessfulInterviews]] as const
 return <div className="mx-auto max-w-6xl space-y-6"><header className="flex flex-wrap items-start justify-between gap-3"><div><Link href="/platform/jobs" className="text-sm text-indigo-700">← {t("back")}</Link><h1 className="mt-2 text-2xl font-bold">{job.title}</h1><div className="mt-2 flex flex-wrap gap-2"><Badge variant="outline">{t(`status.${job.status}` as never)}</Badge><Badge variant="outline" className={job.visibleOnPublicBoard?"border-emerald-200 bg-emerald-50 text-emerald-800":job.discoverable?"border-amber-200 bg-amber-50 text-amber-800":"bg-slate-50 text-slate-600"}>{job.visibleOnPublicBoard?t("visibility.public"):job.discoverable?t("visibility.discoverableHidden"):t("visibility.hidden")}</Badge></div></div><div className="flex gap-2">{job.visibleOnPublicBoard&&<Button variant="outline" render={<Link href={`/jobs/${job.publicId}`}/>}><ExternalLink className="size-4"/>{t("publicPage")}</Button>}<Button variant="outline" onClick={()=>void load()} disabled={loading}><RefreshCw className="size-4"/>{t("refresh")}</Button></div></header>
  {error&&<p role="alert" className="rounded-lg bg-red-50 p-3 text-red-700">{error}</p>}
  <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">{aggregates.map(([key,value])=><article key={key} className="rounded-xl border bg-white p-4"><p className="text-xs uppercase tracking-wide text-slate-500">{t(`aggregates.${key}` as never)}</p><p className="mt-2 text-2xl font-bold">{format.number(value)}</p></article>)}</section>
  <div className="grid gap-6 lg:grid-cols-2"><section className="rounded-xl border bg-white p-5"><h2 className="font-semibold">{t("metadata")}</h2><dl className="mt-4 space-y-3">{metadata.map(([key,value])=><div key={key} className="grid gap-1 border-b pb-3 sm:grid-cols-[150px_1fr]"><dt className="text-sm text-slate-500">{t(`fields.${key}` as never)}</dt><dd className="break-all text-sm">{value||"—"}</dd></div>)}<div className="grid gap-1 sm:grid-cols-[150px_1fr]"><dt className="text-sm text-slate-500">{t("fields.tenant")}</dt><dd><Link href={`/platform/tenants/${job.tenantId}`} className="break-all text-sm text-indigo-700 hover:underline">{job.tenantId}</Link></dd></div></dl></section>
   <section className="space-y-6"><article className="rounded-xl border bg-white p-5"><h2 className="font-semibold">{t("timestamps")}</h2><dl className="mt-4 space-y-3">{[["publishedAt",job.publishedAt],["closingAt",job.closingAt],["updatedAt",job.updatedAt]].map(([key,value])=><div key={key} className="flex justify-between gap-4 border-b pb-3 text-sm"><dt className="text-slate-500">{t(`fields.${key}` as never)}</dt><dd>{value?format.dateTime(new Date(value),{dateStyle:"long",timeStyle:"short"}):"—"}</dd></div>)}</dl></article><article className="rounded-xl border bg-white p-5"><h2 className="font-semibold">{t("visibility.title")}</h2><p className="mt-3 text-sm text-slate-600">{job.visibleOnPublicBoard?t("visibility.publicExplanation"):job.discoverable?t("visibility.discoverableExplanation"):t("visibility.hiddenExplanation")}</p><dl className="mt-4 grid grid-cols-2 gap-3 text-sm"><div className="rounded bg-slate-50 p-3"><dt className="text-slate-500">{t("visibility.discoverable")}</dt><dd className="font-medium">{job.discoverable?t("yes"):t("no")}</dd></div><div className="rounded bg-slate-50 p-3"><dt className="text-slate-500">{t("visibility.visible")}</dt><dd className="font-medium">{job.visibleOnPublicBoard?t("yes"):t("no")}</dd></div></dl></article></section></div>
 </div>
}

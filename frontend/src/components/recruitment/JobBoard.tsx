"use client";

import { FormEvent, useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/i18n/navigation";
import { useSearchParams } from "next/navigation";
import { BriefcaseBusiness, Building2, Clock3, MapPin } from "lucide-react";
import { listPublicJobs, type PublicJob } from "@/lib/recruitment-api";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export function JobBoard({ tenantSlug }: { tenantSlug?: string }) {
  const t = useTranslations("Jobs");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const queryString = searchParams.toString();
  const pathname = usePathname();
  const router = useRouter();
  const [items, setItems] = useState<PublicJob[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState(() => tenantSlug ? tenantSlug
    .split("-").filter(Boolean).map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ") : null);

  useEffect(() => {
    let active = true;
    listPublicJobs(new URLSearchParams(queryString), tenantSlug).then((page) => {
      if (active) {
        setItems(page.items); setCursor(page.nextCursor); setError(null);
        if (tenantSlug && page.items[0]?.companyName) setTenantName(page.items[0].companyName);
      }
    }).catch(() => active && setError(t("loadError"))).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [queryString, tenantSlug, t]);

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const [key, value] of form.entries()) if (String(value).trim()) next.set(key, String(value));
    router.replace(`${pathname}${next.size ? `?${next}` : ""}`);
  }

  async function more() {
    if (!cursor) return;
    setLoading(true);
    try {
      const params = new URLSearchParams(queryString); params.set("cursor", cursor);
      const page = await listPublicJobs(params, tenantSlug);
      setItems((current) => [...current, ...page.items]); setCursor(page.nextCursor);
    } catch { setError(t("loadError")); } finally { setLoading(false); }
  }

  return <>
    <section className="mb-8">
      {tenantSlug && tenantName ? <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-sm font-medium text-indigo-800">
        <Building2 className="h-4 w-4" aria-hidden="true" />
        {t("tenantBoard", { company: tenantName })}
      </div> : <p className="mb-2 text-sm font-semibold uppercase tracking-wider text-indigo-600">{t("eyebrow")}</p>}
      <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">{tenantSlug && tenantName ? t("careerTitle", { company: tenantName }) : t("title")}</h1>
      <p className="mt-3 max-w-2xl text-slate-600">{t("description")}</p>
    </section>
    <form onSubmit={filter} className="mb-8 grid gap-3 rounded-xl border bg-white p-4 shadow-sm md:grid-cols-4" aria-label={t("filters")}>
      <Input name="q" defaultValue={searchParams.get("q") ?? ""} placeholder={t("searchPlaceholder")} className="md:col-span-2" />
      <Input name="location" defaultValue={searchParams.get("location") ?? ""} placeholder={t("location")} />
      <select name="sort" defaultValue={searchParams.get("sort") ?? "newest"} className="h-9 rounded-md border bg-white px-3 text-sm">
        <option value="newest">{t("sortNewest")}</option><option value="relevance">{t("sortRelevance")}</option><option value="closing_soon">{t("sortClosing")}</option>
      </select>
      <select name="employmentType" defaultValue={searchParams.get("employmentType") ?? ""} className="h-9 rounded-md border bg-white px-3 text-sm">
        <option value="">{t("allEmployment")}</option>{(["FULL_TIME","PART_TIME","CONTRACT","TEMPORARY","INTERNSHIP"] as const).map(v=><option key={v} value={v}>{t(`employment.${v}`)}</option>)}
      </select>
      <select name="workMode" defaultValue={searchParams.get("workMode") ?? ""} className="h-9 rounded-md border bg-white px-3 text-sm">
        <option value="">{t("allWorkModes")}</option>{(["ONSITE","REMOTE","HYBRID"] as const).map(v=><option key={v} value={v}>{t(`workMode.${v}`)}</option>)}
      </select>
      <select name="experienceLevel" defaultValue={searchParams.get("experienceLevel") ?? ""} className="h-9 rounded-md border bg-white px-3 text-sm">
        <option value="">{t("allExperience")}</option>{(["ENTRY","JUNIOR","MID","SENIOR","LEAD","EXECUTIVE"] as const).map(v=><option key={v} value={v}>{t(`experience.${v}`)}</option>)}
      </select>
      <Button type="submit" className="bg-indigo-600 text-white hover:bg-indigo-700">{t("search")}</Button>
    </form>
    {error && <div role="alert" className="mb-6 rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">{error}</div>}
    {!loading && !error && items.length===0 && <div className="rounded-xl border border-dashed bg-white p-10 text-center"><h2 className="font-semibold">{t("emptyTitle")}</h2><p className="mt-2 text-sm text-slate-600">{t("emptyDescription")}</p></div>}
    <div className="grid gap-4">
      {items.map(job=><article key={job.publicId} className="rounded-xl border bg-white p-5 shadow-sm transition hover:border-indigo-200 hover:shadow-md">
        <div className="flex flex-col justify-between gap-4 sm:flex-row">
          <div><p className="text-sm font-medium text-indigo-600">{job.companyName}</p><h2 className="mt-1 text-xl font-semibold text-slate-950"><Link href={`${locale==="vi"?"/vi":""}/jobs/${job.publicId}`} className="hover:text-indigo-700">{job.title}</Link></h2>
            <div className="mt-3 flex flex-wrap gap-3 text-sm text-slate-600">{job.location&&<span className="inline-flex items-center gap-1"><MapPin className="h-4 w-4" />{job.location}</span>}{job.employmentType&&<span className="inline-flex items-center gap-1"><BriefcaseBusiness className="h-4 w-4" />{t(`employment.${job.employmentType}`)}</span>}{job.workMode&&<span>{t(`workMode.${job.workMode}`)}</span>}</div>
          </div><div className="shrink-0 text-sm text-slate-500"><span className="inline-flex items-center gap-1"><Clock3 className="h-4 w-4" />{t("closes",{date:new Intl.DateTimeFormat(locale,{dateStyle:"medium"}).format(new Date(job.closingAt))})}</span></div>
        </div>
      </article>)}
    </div>
    {cursor&&<div className="mt-8 text-center"><Button type="button" variant="outline" onClick={more} disabled={loading}>{loading?t("loading"):t("loadMore")}</Button></div>}
    {loading&&items.length===0&&<p role="status" className="py-12 text-center text-slate-600">{t("loading")}</p>}
  </>;
}

"use client";

import { useTranslations } from "next-intl";
import { useEffect,useState } from "react";
import { useAuthStore } from "@/components/providers/StoreProvider";
import { Link, usePathname } from "@/i18n/navigation";
import { cn } from "@/lib/utils";
import { useApiClient } from "@/hooks/useApiClient";
import { getRecruitmentCapabilities,type RecruitmentCapabilities } from "@/lib/recruitment-admin-api";
import { publicConfig } from "@/lib/public-config";

const items = ["overview","jobs","applications","candidates","templates","schedule","interviews","usage","setup"] as const;

export function RecruitmentLayout({children}:{children:React.ReactNode}){
  const t=useTranslations("Recruitment");const pathname=usePathname();const role=useAuthStore(state=>state.user?.role);
  const {request}=useApiClient();const [capabilities,setCapabilities]=useState<RecruitmentCapabilities|null>(null);
  useEffect(()=>{if(!publicConfig.recruitmentEnabled)return;const controller=new AbortController();getRecruitmentCapabilities(request,controller.signal).then(setCapabilities).catch(()=>setCapabilities(null));return()=>controller.abort();},[request]);
  if(!publicConfig.recruitmentEnabled||capabilities&&!capabilities.masterEnabled)return <div role="status" className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-amber-950"><h2 className="font-semibold">{t("title")}</h2><p className="mt-2 text-sm">{capabilities?.blockers.join(", ")||"Recruitment is not available for this deployment."}</p></div>;
  if(!capabilities)return <p role="status" className="text-sm text-slate-600">Loading…</p>;
  const visible=items.filter(item=>item!=="setup"||role==="TENANT_ADMIN");
  const href=(item:typeof items[number])=>item==="overview"?"/recruitment":`/recruitment/${item}`;
  const nav=<nav aria-label={t("secondaryNav")} className="flex gap-1 xl:flex-col">{visible.map(item=>{const target=href(item);const active=pathname===target||(item!=="overview"&&pathname.startsWith(`${target}/`));return <Link key={item} href={target} className={cn("shrink-0 rounded-md px-3 py-2 text-sm font-medium transition-colors",active?"bg-indigo-600 text-white":"text-slate-600 hover:bg-slate-100 hover:text-slate-950")}>{t(`nav.${item}`)}</Link>;})}</nav>;
  return <div className="space-y-4"><div className="flex flex-wrap items-center gap-2"><h2 className="text-xl font-semibold text-slate-900">{t("title")}</h2><span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-semibold text-indigo-700">{t("beta")}</span></div><div className="overflow-x-auto rounded-lg border bg-white p-2 xl:hidden">{nav}</div><div className="xl:grid xl:grid-cols-[180px_minmax(0,1fr)] xl:gap-6"><aside className="hidden rounded-lg border bg-white p-2 xl:block">{nav}</aside><section className="min-w-0">{children}</section></div></div>;
}

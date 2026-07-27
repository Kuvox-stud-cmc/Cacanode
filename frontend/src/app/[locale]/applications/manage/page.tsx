import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { PublicJobsLayout } from "@/components/recruitment/PublicJobsLayout";
import { CandidateManagement } from "@/components/recruitment/CandidateManagement";
import type { AppLocale } from "@/i18n/routing";

export const metadata:Metadata={robots:{index:false,follow:false},referrer:"no-referrer"};
export default async function ManagePage({params,searchParams}:{params:Promise<{locale:AppLocale}>;searchParams:Promise<Record<string,string|string[]|undefined>>}){const [{locale},query]=await Promise.all([params,searchParams]);setRequestLocale(locale);const t=await getTranslations({locale,namespace:"Jobs.manage"});const scheduling=typeof query.invitation==="string";return <PublicJobsLayout><div className="mx-auto max-w-4xl"><h1 className="mb-2 text-3xl font-bold">{t(scheduling?"invitation.pageTitle":"title")}</h1><p className="mb-7 text-slate-600">{t(scheduling?"invitation.pageDescription":"description")}</p><CandidateManagement/></div></PublicJobsLayout>;}

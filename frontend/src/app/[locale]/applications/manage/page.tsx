import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { PublicJobsLayout } from "@/components/recruitment/PublicJobsLayout";
import { CandidateManagement } from "@/components/recruitment/CandidateManagement";
import type { AppLocale } from "@/i18n/routing";

export const metadata:Metadata={robots:{index:false,follow:false}};
export default async function ManagePage({params}:{params:Promise<{locale:AppLocale}>}){const {locale}=await params;setRequestLocale(locale);const t=await getTranslations({locale,namespace:"Jobs.manage"});return <PublicJobsLayout><div className="mx-auto max-w-2xl"><h1 className="mb-2 text-3xl font-bold">{t("title")}</h1><p className="mb-7 text-slate-600">{t("description")}</p><CandidateManagement/></div></PublicJobsLayout>;}

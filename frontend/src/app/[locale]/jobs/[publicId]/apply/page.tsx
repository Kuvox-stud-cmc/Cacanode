import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { PublicJobsLayout } from "@/components/recruitment/PublicJobsLayout";
import { ApplicationForm } from "@/components/recruitment/ApplicationForm";
import { getPublicJob } from "@/lib/recruitment-api";
import type { AppLocale } from "@/i18n/routing";

export const metadata:Metadata={robots:{index:false,follow:false}};
export default async function ApplyPage({params}:{params:Promise<{locale:AppLocale;publicId:string}>}){const {locale,publicId}=await params;setRequestLocale(locale);const t=await getTranslations({locale,namespace:"Jobs.apply"});let job;try{job=await getPublicJob(publicId);}catch{notFound();}return <PublicJobsLayout><div className="mx-auto max-w-2xl"><p className="text-sm font-medium text-indigo-600">{job.companyName}</p><h1 className="mt-1 text-3xl font-bold">{t("title",{job:job.title})}</h1><p className="mb-7 mt-3 text-slate-600">{t("description")}</p><ApplicationForm job={job}/></div></PublicJobsLayout>;}

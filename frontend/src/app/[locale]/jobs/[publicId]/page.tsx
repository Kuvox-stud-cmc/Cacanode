import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { PublicJobsLayout } from "@/components/recruitment/PublicJobsLayout";
import { getPublicJob } from "@/lib/recruitment-api";
import { jobPostingJsonLd, publicJobMetadata, tenantCareersPath } from "@/lib/recruitment-seo";
import { Link } from "@/i18n/navigation";
import { Button } from "@/components/ui/button";
import { JobPostPresentation } from "@/components/recruitment/JobPostPresentation";
import type { AppLocale } from "@/i18n/routing";
import NextLink from "next/link";

type Props={params:Promise<{locale:AppLocale;publicId:string}>};
export async function generateMetadata({params}:Props):Promise<Metadata>{const {locale,publicId}=await params;try{return publicJobMetadata(locale,await getPublicJob(publicId));}catch{return{};}}
export default async function JobPage({params}:Props){const {locale,publicId}=await params;setRequestLocale(locale);const t=await getTranslations({locale,namespace:"Jobs.detail"});let job;try{job=await getPublicJob(publicId);}catch{notFound();}const jsonLd=jobPostingJsonLd(job);return <PublicJobsLayout>{jsonLd&&<script type="application/ld+json" dangerouslySetInnerHTML={{__html:JSON.stringify(jsonLd).replace(/</g,"\\u003c")}}/>}<JobPostPresentation job={job} backLink={<Link href={tenantCareersPath(job.tenantSlug)} className="text-sm font-medium text-indigo-600 hover:text-indigo-800">← {t("allJobs")}</Link>} footer={<div className="mt-9 flex flex-col items-start justify-between gap-4 border-t pt-6 sm:flex-row sm:items-center"><p className="text-sm text-slate-500">{t("closing",{date:new Intl.DateTimeFormat(locale,{dateStyle:"long"}).format(new Date(job.closingAt))})}</p><NextLink href={`${locale==="vi"?"/vi":""}/jobs/${publicId}/apply`}><Button className="bg-indigo-600 text-white hover:bg-indigo-700">{t("apply")}</Button></NextLink></div>}/></PublicJobsLayout>;}

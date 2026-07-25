import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { JobBoard } from "@/components/recruitment/JobBoard";
import { PublicJobsLayout } from "@/components/recruitment/PublicJobsLayout";
import { languageAlternates, localizedPath } from "@/lib/recruitment-seo";
import type { AppLocale } from "@/i18n/routing";
import { Suspense } from "react";

type Props={params:Promise<{locale:AppLocale;tenantSlug:string}>};
export async function generateMetadata({params}:Props):Promise<Metadata>{const {locale,tenantSlug}=await params;const t=await getTranslations({locale,namespace:"Jobs"});const path=`/careers/${tenantSlug}`;return{title:t("careerMetaTitle"),description:t("metaDescription"),alternates:{canonical:localizedPath(locale,path),languages:languageAlternates(path)}};}
export default async function CareersPage({params}:Props){const {locale,tenantSlug}=await params;setRequestLocale(locale);return <PublicJobsLayout><Suspense fallback={<p className="py-12 text-center text-slate-600">Loading…</p>}><JobBoard tenantSlug={tenantSlug}/></Suspense></PublicJobsLayout>;}

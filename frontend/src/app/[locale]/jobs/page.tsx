import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { JobBoard } from "@/components/recruitment/JobBoard";
import { PublicJobsLayout } from "@/components/recruitment/PublicJobsLayout";
import { languageAlternates, localizedPath } from "@/lib/recruitment-seo";
import type { AppLocale } from "@/i18n/routing";
import { Suspense } from "react";

export async function generateMetadata({params}:{params:Promise<{locale:AppLocale}>}):Promise<Metadata>{const {locale}=await params;const t=await getTranslations({locale,namespace:"Jobs"});return{title:t("metaTitle"),description:t("metaDescription"),alternates:{canonical:localizedPath(locale,"/jobs"),languages:languageAlternates("/jobs")}};}
export default async function JobsPage({params}:{params:Promise<{locale:AppLocale}>}){const {locale}=await params;setRequestLocale(locale);return <PublicJobsLayout><Suspense fallback={<p className="py-12 text-center text-slate-600">Loading…</p>}><JobBoard/></Suspense></PublicJobsLayout>;}

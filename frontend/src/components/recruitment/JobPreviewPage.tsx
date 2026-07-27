"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { getRecruitmentJobPreview, type RecruitmentJobPreview } from "@/lib/recruitment-admin-api";
import { JobPostPresentation } from "@/components/recruitment/JobPostPresentation";

export function JobPreviewPage({ jobId }: { jobId: string }) {
  const { request } = useApiClient();
  const locale = useLocale();
  const t = useTranslations("Recruitment.forms");
  const [job, setJob] = useState<RecruitmentJobPreview | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getRecruitmentJobPreview(request, jobId).then((value) => { if (active) setJob(value); })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : t("previewError")); });
    return () => { active = false; };
  }, [jobId, request, t]);

  if (error) return <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">{error}</p>;
  if (!job) return <p role="status" className="p-8 text-center text-sm text-muted-foreground">{t("previewLoading")}</p>;

  return <JobPostPresentation
    job={job}
    previewLabel={t("recruiterPreview")}
    previewStatus={job.status}
    backLink={<Link href={`/recruitment/jobs/${jobId}`} className="text-sm font-medium text-indigo-600 hover:text-indigo-800">← {t("backToJob")}</Link>}
    footer={<div className="mt-9 border-t pt-6"><p className="text-sm text-slate-500">{job.closingAt ? t("previewClosing", { date: new Intl.DateTimeFormat(locale, { dateStyle: "long" }).format(new Date(job.closingAt)) }) : t("previewNoClosing")}</p><p className="mt-2 text-sm font-medium text-amber-800">{t("previewNoApply")}</p></div>}
  />;
}

import type { ReactNode } from "react";

export type JobPost = {
  title: string;
  description: string;
  descriptionHtml: string | null;
  companyName: string;
  department: string | null;
  location: string | null;
  employmentType: string | null;
  workMode: string | null;
  closingAt: string | null;
};

export function JobDescription({ job }: { job: Pick<JobPost, "description" | "descriptionHtml"> }) {
  if (!job.descriptionHtml) return <div className="mt-8 whitespace-pre-wrap leading-7 text-slate-700">{job.description}</div>;
  return <div
    className="mt-8 leading-7 text-slate-700 [&_a]:font-medium [&_a]:text-indigo-700 [&_a]:underline [&_a]:underline-offset-2 [&_blockquote]:my-5 [&_blockquote]:border-l-4 [&_blockquote]:border-indigo-200 [&_blockquote]:pl-4 [&_blockquote]:italic [&_h2]:mb-3 [&_h2]:mt-8 [&_h2]:text-2xl [&_h2]:font-bold [&_h2]:text-slate-950 [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:text-xl [&_h3]:font-semibold [&_h3]:text-slate-950 [&_li]:my-1 [&_ol]:my-4 [&_ol]:list-decimal [&_ol]:pl-6 [&_p]:my-4 [&_strong]:font-semibold [&_ul]:my-4 [&_ul]:list-disc [&_ul]:pl-6"
    dangerouslySetInnerHTML={{ __html: job.descriptionHtml }}
  />;
}

export function JobPostPresentation({ job, backLink, footer, previewStatus, previewLabel }: {
  job: JobPost;
  backLink?: ReactNode;
  footer?: ReactNode;
  previewStatus?: string;
  previewLabel?: string;
}) {
  return <article className="mx-auto max-w-3xl">
    {backLink}
    {previewStatus && <div role="status" className="mt-5 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950"><strong>{previewLabel}</strong> · {previewStatus}</div>}
    <div className="mt-5 rounded-xl border bg-white p-6 shadow-sm sm:p-9">
      <p className="font-medium text-indigo-600">{job.companyName}</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">{job.title}</h1>
      <div className="mt-4 flex flex-wrap gap-2 text-sm text-slate-600">
        {job.location && <span>{job.location}</span>}
        {job.department && <span>• {job.department}</span>}
        {job.employmentType && <span>• {job.employmentType.replaceAll("_", " ")}</span>}
        {job.workMode && <span>• {job.workMode}</span>}
      </div>
      <JobDescription job={job} />
      {footer}
    </div>
  </article>;
}

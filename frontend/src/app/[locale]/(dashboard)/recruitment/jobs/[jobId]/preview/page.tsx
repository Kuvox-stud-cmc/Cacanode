import { JobPreviewPage } from "@/components/recruitment/JobPreviewPage";

export default async function Page({ params }: { params: Promise<{ jobId: string }> }) {
  const { jobId } = await params;
  return <JobPreviewPage jobId={jobId} />;
}

import { JobForm } from "@/components/recruitment/JobForm";

export default async function Page({ params }: { params: Promise<{ jobId: string }> }) {
  const { jobId } = await params;
  return <JobForm jobId={jobId} />;
}

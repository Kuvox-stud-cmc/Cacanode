import { InterviewDetailPage } from "@/components/recruitment/InterviewDetailPage";

export default async function Page({ params }: { params: Promise<{ interviewId: string }> }) {
  const { interviewId } = await params;
  return <InterviewDetailPage interviewId={interviewId} />;
}

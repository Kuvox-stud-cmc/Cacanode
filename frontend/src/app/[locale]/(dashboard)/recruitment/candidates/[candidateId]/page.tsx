import { CandidateDetailPage } from "@/components/recruitment/CandidateDetailPage";

export default async function Page({ params }: { params: Promise<{ candidateId: string }> }) {
  const { candidateId } = await params;
  return <CandidateDetailPage candidateId={candidateId} />;
}

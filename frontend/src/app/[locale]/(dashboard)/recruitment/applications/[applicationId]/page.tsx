import { ApplicationDetailPage } from "@/components/recruitment/ApplicationDetailPage";

export default async function Page({ params }: { params: Promise<{ applicationId: string }> }) {
  const { applicationId } = await params;
  return <ApplicationDetailPage applicationId={applicationId} />;
}

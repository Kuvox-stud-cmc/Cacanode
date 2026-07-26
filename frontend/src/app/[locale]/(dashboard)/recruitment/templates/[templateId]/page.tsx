import { TemplateForm } from "@/components/recruitment/TemplateForm";

export default async function Page({ params }: { params: Promise<{ templateId: string }> }) {
  const { templateId } = await params;
  return <TemplateForm templateId={templateId} />;
}

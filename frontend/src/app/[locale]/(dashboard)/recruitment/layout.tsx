import { notFound } from "next/navigation";
import { RecruitmentLayout } from "@/components/recruitment/RecruitmentLayout";
import { publicConfig } from "@/lib/public-config";

export default function Layout({children}:{children:React.ReactNode}){
  if(!publicConfig.recruitmentEnabled)notFound();
  return <RecruitmentLayout>{children}</RecruitmentLayout>;
}

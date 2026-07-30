import { Redirect,useLocalSearchParams } from 'expo-router';
import { RecruitmentSectionScreen } from '@/features/recruitment/screens/recruitment-section-screen';
import type { RecruitmentSection } from '@/features/recruitment/screens/recruitment-home-screen';
const allowed=new Set<RecruitmentSection>(['overview','jobs','applications','candidates','templates','schedule','interviews','usage','setup']);
export default function RecruitmentSectionRoute(){const{section}=useLocalSearchParams<{section:string}>();if(!section||!allowed.has(section as RecruitmentSection))return <Redirect href="/dashboard"/>;return <RecruitmentSectionScreen section={section as RecruitmentSection}/>}

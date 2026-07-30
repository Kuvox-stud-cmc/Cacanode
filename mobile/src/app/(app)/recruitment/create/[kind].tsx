import { Redirect,useLocalSearchParams } from 'expo-router';
import { RecruitmentCreateScreen,type RecruitmentCreateKind } from '@/features/recruitment/screens/recruitment-create-screen';
const allowed=new Set<RecruitmentCreateKind>(['job','candidate','application','template']);
export default function RecruitmentCreateRoute(){const{kind}=useLocalSearchParams<{kind:string}>();if(!kind||!allowed.has(kind as RecruitmentCreateKind))return <Redirect href="/dashboard"/>;return <RecruitmentCreateScreen kind={kind as RecruitmentCreateKind}/>}


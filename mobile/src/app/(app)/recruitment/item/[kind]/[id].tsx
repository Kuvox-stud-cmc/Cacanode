import { Redirect,useLocalSearchParams } from 'expo-router';
import { RecruitmentItemScreen,type RecruitmentItemKind } from '@/features/recruitment/screens/recruitment-item-screen';
const allowed=new Set<RecruitmentItemKind>(['job','candidate','application','template','interview']);
export default function RecruitmentItemRoute(){const{kind,id}=useLocalSearchParams<{kind:string;id:string}>();if(!kind||!id||!allowed.has(kind as RecruitmentItemKind))return <Redirect href="/dashboard"/>;return <RecruitmentItemScreen kind={kind as RecruitmentItemKind} id={id}/>}

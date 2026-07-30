import { useRouter } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { StyleSheet, View } from 'react-native';

import { ErrorState } from '@/components/feedback/error-state';
import { StateView } from '@/components/feedback/state-view';
import { ScrollScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { ListRow } from '@/components/ui/list-row';
import { env } from '@/constants/env';
import { spacing } from '@/constants/theme';
import { useGetRecruitmentCapabilitiesQuery } from '@/features/recruitment/api/recruitment-api';

const sections=['overview','jobs','applications','candidates','templates','schedule','interviews','usage','setup'] as const;
export type RecruitmentSection=typeof sections[number];

export function RecruitmentHomeScreen(){
  const {t}=useTranslation();const router=useRouter();
  const {data,isLoading,isError,refetch}=useGetRecruitmentCapabilitiesQuery(undefined,{skip:!env.recruitmentEnabled});
  if(!env.recruitmentEnabled)return <StateView title={t('recruitment.disabled')}/>;
  if(isLoading)return <StateView loading title={t('common.loading')}/>;
  if(isError)return <ErrorState title={t('recruitment.loadFailed')} retryLabel={t('common.retry')} onRetry={()=>void refetch()}/>;
  if(!data?.masterEnabled)return <StateView title={t('recruitment.disabled')} description={data?.blockers.join(', ')}/>;
  return <ScrollScreen contentContainerStyle={styles.content}><View style={styles.heading}><AppText accessibilityRole="header" variant="title">{t('recruitment.title')}</AppText><AppText muted>{t('recruitment.description')}</AppText></View><Card elevated>{sections.map(section=><ListRow key={section} title={t(`recruitment.sections.${section}`)} subtitle={t(`recruitment.descriptions.${section}`)} onPress={()=>router.push({pathname:'/(app)/recruitment/[section]',params:{section}} as never)} trailing={<Badge tone="neutral">›</Badge>}/>)}</Card></ScrollScreen>;
}
const styles=StyleSheet.create({content:{gap:spacing.xl,paddingVertical:spacing.xxl},heading:{gap:spacing.sm}});

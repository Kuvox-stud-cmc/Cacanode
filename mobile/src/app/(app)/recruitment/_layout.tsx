import { Stack } from 'expo-router';
import { useTranslation } from 'react-i18next';
export default function RecruitmentLayout(){const{t}=useTranslation();return <Stack screenOptions={{headerBackButtonDisplayMode:'minimal'}}><Stack.Screen name="[section]" options={{title:t('nav.recruitment')}}/><Stack.Screen name="create/[kind]" options={{title:t('nav.recruitment')}}/><Stack.Screen name="item/[kind]/[id]" options={{title:t('nav.recruitment')}}/></Stack>}

import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { AppText } from '@/components/ui/app-text';
import { spacing } from '@/constants/theme';
import { changeAppLanguage, type AppLanguage } from '@/i18n';
import { useAppTheme } from '@/hooks/use-app-theme';

export function LanguageSelector({ compact = false }: { compact?: boolean }) {
  const { i18n, t } = useTranslation(); const theme=useAppTheme();
  const selected:AppLanguage=i18n.resolvedLanguage?.startsWith('vi')?'vi':'en';
  return <View accessibilityRole="radiogroup" style={styles.group}>
    {!compact?<AppText muted variant="bodySmall">{t('language.label')}</AppText>:null}
    <View style={styles.options}>{(['en','vi'] as const).map(language=><Pressable key={language} accessibilityRole="radio" accessibilityState={{checked:selected===language}} onPress={()=>void changeAppLanguage(language)} style={[styles.option,{borderColor:selected===language?theme.colors.primary:theme.colors.border,backgroundColor:selected===language?theme.colors.primarySoft:theme.colors.surface}]}><AppText variant="bodySmall">{t(language==='en'?'language.english':'language.vietnamese')}</AppText></Pressable>)}</View>
  </View>;
}
const styles=StyleSheet.create({group:{gap:spacing.xs},options:{flexDirection:'row',gap:spacing.sm},option:{borderWidth:1,borderRadius:999,minHeight:40,justifyContent:'center',paddingHorizontal:spacing.md}});

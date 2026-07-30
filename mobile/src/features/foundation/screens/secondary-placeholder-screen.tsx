import { useLocalSearchParams } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { StyleSheet, View } from 'react-native';

import { ScrollScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { spacing } from '@/constants/theme';

export function SecondaryPlaceholderScreen({
  description,
  eyebrow,
  parameter,
  phase,
  title,
}: {
  description: string;
  eyebrow: string;
  parameter?: string;
  phase: number;
  title: string;
}) {
  const { t } = useTranslation();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const rawValue = parameter ? params[parameter] : undefined;
  const value = Array.isArray(rawValue) ? rawValue[0] : rawValue;

  return (
    <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.screen}>
      <View style={styles.heading}>
        <AppText style={styles.eyebrow} variant="bodySmall">{eyebrow}</AppText>
        <AppText accessibilityRole="header" variant="title">{title}</AppText>
        <AppText muted>{description}</AppText>
      </View>
      <Card elevated style={styles.card}>
        <Badge tone="primary">{t('foundation.phase', { phase })}</Badge>
        {value ? <AppText muted variant="bodySmall">{t('foundation.reference', { value })}</AppText> : null}
        <AppText>{t('foundation.ready')}</AppText>
      </Card>
    </ScrollScreen>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.lg },
  eyebrow: { fontWeight: '700', textTransform: 'uppercase' },
  heading: { gap: spacing.sm },
  screen: { gap: spacing.xl },
});

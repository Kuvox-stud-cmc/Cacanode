import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { spacing } from '@/constants/theme';

export function DashboardSkeleton() {
  const { t } = useTranslation();
  return (
    <View accessibilityLabel={t('accessibility.loadingDashboard')} accessibilityRole="progressbar" style={styles.content}>
      <View style={styles.heading}>
        <Skeleton height={32} width="64%" />
        <Skeleton width="46%" />
      </View>
      <Card style={styles.section}>
        <Skeleton height={24} width="38%" />
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton height={54} key={index} />
        ))}
      </Card>
      <View style={styles.metrics}>
        {Array.from({ length: 4 }, (_, index) => (
          <Card key={index} style={styles.metric}>
            <Skeleton width="42%" />
            <Skeleton height={30} width="32%" />
            <Skeleton width="58%" />
          </Card>
        ))}
      </View>
      <Card style={styles.section}>
        <Skeleton height={24} width="42%" />
        {Array.from({ length: 3 }, (_, index) => (
          <Skeleton height={60} key={index} />
        ))}
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.xl },
  heading: { gap: spacing.sm },
  metric: { gap: spacing.sm },
  metrics: { gap: spacing.lg },
  section: { gap: spacing.lg },
});

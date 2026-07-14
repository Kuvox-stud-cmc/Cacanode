import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { Card } from '@/components/ui/card';
import { radii, spacing } from '@/constants/theme';
import type { DashboardMetric } from '@/features/dashboard/model/dashboard-view-model';
import { useAppTheme } from '@/hooks/use-app-theme';

export function DashboardMetricCard({
  metric,
  progress,
}: {
  metric: DashboardMetric;
  progress?: number;
}) {
  const theme = useAppTheme();

  return (
    <Card elevated style={styles.card}>
      <AppText muted style={styles.title} variant="bodySmall">{metric.title}</AppText>
      <AppText variant="title">{metric.value}</AppText>
      {progress !== undefined ? (
        <View
          accessibilityLabel={`Storage usage ${progress}%`}
          accessibilityRole="progressbar"
          accessibilityValue={{ max: 100, min: 0, now: progress }}
          style={[styles.track, { backgroundColor: theme.colors.border }]}>
          <View
            style={[
              styles.progress,
              { backgroundColor: theme.colors.primary, width: `${progress}%` },
            ]}
          />
        </View>
      ) : null}
      <AppText muted variant="bodySmall">{metric.detail}</AppText>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.sm },
  progress: { borderRadius: radii.pill, height: '100%' },
  title: { fontWeight: '600' },
  track: { borderRadius: radii.pill, height: 8, overflow: 'hidden', width: '100%' },
});

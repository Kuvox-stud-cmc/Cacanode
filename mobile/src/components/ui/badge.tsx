import type { PropsWithChildren } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { radii, spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger';

export function Badge({ children, tone = 'neutral' }: PropsWithChildren<{ tone?: BadgeTone }>) {
  const theme = useAppTheme();
  const color = {
    neutral: theme.colors.textMuted,
    primary: theme.colors.primaryText,
    success: theme.colors.successText,
    warning: theme.colors.warningText,
    danger: theme.colors.dangerText,
  }[tone];

  return (
    <View
      accessibilityRole="text"
      style={[styles.badge, { backgroundColor: `${color}1F`, borderColor: `${color}55` }]}>
      <AppText numberOfLines={2} style={[styles.label, { color }]} variant="caption">
        {children}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-start',
    borderRadius: radii.pill,
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
  },
  label: { fontWeight: '700' },
});

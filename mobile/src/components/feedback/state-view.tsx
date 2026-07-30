import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { radii, spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useTranslation } from 'react-i18next';

type StateViewProps = {
  actionLabel?: string;
  description?: string;
  icon?: ReactNode;
  loading?: boolean;
  onAction?: () => void;
  title: string;
};

export function StateView({
  actionLabel,
  description,
  icon,
  loading = false,
  onAction,
  title,
}: StateViewProps) {
  const theme = useAppTheme();
  const {t}=useTranslation();

  return (
    <View accessibilityLiveRegion="polite" style={styles.container}>
      {loading ? (
        <ActivityIndicator accessibilityLabel={t('accessibility.loading')} color={theme.colors.primary} size="large" />
      ) : (
        icon
      )}
      <AppText accessibilityRole="header" style={styles.centered} variant="heading">
        {title}
      </AppText>
      {description ? (
        <AppText muted style={styles.centered} variant="bodySmall">
          {description}
        </AppText>
      ) : null}
      {actionLabel && onAction ? (
        <Pressable
          accessibilityRole="button"
          onPress={onAction}
          style={({ pressed }) => [
            styles.action,
            { backgroundColor: pressed ? theme.colors.primaryPressed : theme.colors.primary },
          ]}>
          <AppText style={styles.actionText} variant="bodySmall">
            {actionLabel}
          </AppText>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  action: {
    borderRadius: radii.md,
    minHeight: 44,
    justifyContent: 'center',
    marginTop: spacing.sm,
    paddingHorizontal: spacing.xl,
  },
  actionText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  centered: {
    textAlign: 'center',
  },
  container: {
    alignItems: 'center',
    gap: spacing.md,
    justifyContent: 'center',
    padding: spacing.xxl,
  },
});

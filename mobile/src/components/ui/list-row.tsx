import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View, type PressableProps } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type ListRowProps = Omit<PressableProps, 'children' | 'style'> & {
  leading?: ReactNode;
  subtitle?: string;
  title: string;
  trailing?: ReactNode;
};

export function ListRow({
  accessibilityLabel,
  disabled,
  leading,
  onPress,
  subtitle,
  title,
  trailing,
  ...props
}: ListRowProps) {
  const theme = useAppTheme();
  const content = (
    <>
      {leading ? <View style={styles.leading}>{leading}</View> : null}
      <View style={styles.copy}>
        <AppText style={styles.title}>{title}</AppText>
        {subtitle ? <AppText muted variant="bodySmall">{subtitle}</AppText> : null}
      </View>
      {trailing ? <View style={styles.trailing}>{trailing}</View> : null}
    </>
  );

  if (!onPress) return <View style={styles.row}>{content}</View>;

  return (
    <Pressable
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityRole="button"
      accessibilityState={{ disabled: Boolean(disabled) }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        pressed && { backgroundColor: theme.colors.primarySoft },
        disabled && styles.disabled,
      ]}
      {...props}>
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  copy: { flex: 1, gap: spacing.xs, minWidth: 0 },
  disabled: { opacity: 0.55 },
  leading: { flexShrink: 0 },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing.md,
    minHeight: 52,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
  title: { fontWeight: '600' },
  trailing: { flexShrink: 0 },
});

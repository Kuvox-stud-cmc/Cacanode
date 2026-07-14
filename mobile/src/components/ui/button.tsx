import type { PropsWithChildren } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  type PressableProps,
  type ViewStyle,
} from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { radii, spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

type ButtonProps = PropsWithChildren<
  Omit<PressableProps, 'children' | 'style'> & {
    loading?: boolean;
    style?: ViewStyle;
    variant?: ButtonVariant;
  }
>;

export function Button({
  accessibilityLabel,
  children,
  disabled,
  loading = false,
  style,
  variant = 'primary',
  ...props
}: ButtonProps) {
  const theme = useAppTheme();
  const unavailable = disabled || loading;
  const colors = {
    primary: {
      background: theme.colors.primary,
      pressed: theme.colors.primaryPressed,
      border: theme.colors.primary,
      text: '#FFFFFF',
    },
    secondary: {
      background: theme.colors.surface,
      pressed: theme.colors.primarySoft,
      border: theme.colors.border,
      text: theme.colors.text,
    },
    ghost: {
      background: 'transparent',
      pressed: theme.colors.primarySoft,
      border: 'transparent',
      text: theme.colors.primaryText,
    },
    danger: {
      background: theme.colors.danger,
      pressed: theme.colors.dangerPressed,
      border: theme.colors.danger,
      text: '#FFFFFF',
    },
  }[variant];

  return (
    <Pressable
      accessibilityLabel={accessibilityLabel ?? (typeof children === 'string' ? children : undefined)}
      accessibilityRole="button"
      accessibilityState={{ disabled: Boolean(unavailable), busy: loading }}
      disabled={unavailable}
      style={({ pressed }) => [
        styles.button,
        {
          backgroundColor: pressed ? colors.pressed : colors.background,
          borderColor: colors.border,
        },
        unavailable && styles.disabled,
        style,
      ]}
      {...props}>
      {loading ? (
        <ActivityIndicator accessibilityLabel="Loading" color={colors.text} />
      ) : (
        <AppText style={[styles.label, { color: colors.text }]}>{children}</AppText>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    borderRadius: radii.md,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 48,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  disabled: { opacity: 0.55 },
  label: { fontWeight: '700', textAlign: 'center' },
});

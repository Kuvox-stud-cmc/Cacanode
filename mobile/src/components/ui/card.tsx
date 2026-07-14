import type { PropsWithChildren } from 'react';
import { StyleSheet, View, type ViewProps } from 'react-native';

import { elevation, radii, spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type CardProps = PropsWithChildren<ViewProps & { elevated?: boolean }>;

export function Card({ children, elevated = false, style, ...props }: CardProps) {
  const theme = useAppTheme();
  return (
    <View
      style={[
        styles.card,
        elevated && elevation.small,
        { backgroundColor: theme.colors.surface, borderColor: theme.colors.border },
        style,
      ]}
      {...props}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radii.lg,
    borderWidth: StyleSheet.hairlineWidth,
    padding: spacing.xl,
  },
});

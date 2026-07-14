import { StyleSheet, Text, type TextProps } from 'react-native';

import { typography } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

export type AppTextVariant = keyof typeof typography;

type AppTextProps = TextProps & {
  muted?: boolean;
  variant?: AppTextVariant;
};

export function AppText({ muted = false, style, variant = 'body', ...props }: AppTextProps) {
  const theme = useAppTheme();

  return (
    <Text
      style={[
        styles.base,
        typography[variant],
        { color: muted ? theme.colors.textMuted : theme.colors.text },
        style,
      ]}
      {...props}
    />
  );
}

const styles = StyleSheet.create({
  base: {
    fontFamily: undefined,
  },
});

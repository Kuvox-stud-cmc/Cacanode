import { StyleSheet, View, type ViewStyle } from 'react-native';

import { useAppTheme } from '@/hooks/use-app-theme';

export function Separator({ style }: { style?: ViewStyle }) {
  const theme = useAppTheme();
  return <View accessibilityRole="none" style={[styles.separator, { backgroundColor: theme.colors.border }, style]} />;
}

const styles = StyleSheet.create({
  separator: { height: StyleSheet.hairlineWidth, width: '100%' },
});

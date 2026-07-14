import { useEffect, useState } from 'react';
import { Animated, StyleSheet, type ViewStyle } from 'react-native';

import { radii } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

export function Skeleton({ height = 18, style, width = '100%' }: { height?: number; style?: ViewStyle; width?: ViewStyle['width'] }) {
  const theme = useAppTheme();
  const [opacity] = useState(() => new Animated.Value(0.45));

  useEffect(() => {
    const animation = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { duration: 650, toValue: 0.9, useNativeDriver: true }),
        Animated.timing(opacity, { duration: 650, toValue: 0.45, useNativeDriver: true }),
      ]),
    );
    animation.start();
    return () => animation.stop();
  }, [opacity]);

  return (
    <Animated.View
      accessibilityLabel="Loading content"
      accessibilityRole="progressbar"
      style={[styles.skeleton, { backgroundColor: theme.colors.border, height, opacity, width }, style]}
    />
  );
}

const styles = StyleSheet.create({ skeleton: { borderRadius: radii.sm } });

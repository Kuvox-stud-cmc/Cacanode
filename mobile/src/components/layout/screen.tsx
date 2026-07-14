import type { PropsWithChildren } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  View,
  type ScrollViewProps,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView, type Edge } from 'react-native-safe-area-context';

import { spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type ScreenProps = PropsWithChildren<{
  edges?: Edge[];
  style?: ViewStyle;
}>;

type ScrollScreenProps = ScreenProps & {
  contentContainerStyle?: ViewStyle;
  keyboardShouldPersistTaps?: ScrollViewProps['keyboardShouldPersistTaps'];
  refreshControl?: ScrollViewProps['refreshControl'];
};

export function Screen({ children, edges = ['top', 'right', 'bottom', 'left'], style }: ScreenProps) {
  const theme = useAppTheme();

  return (
    <SafeAreaView
      edges={edges}
      style={[styles.screen, { backgroundColor: theme.colors.background }, style]}>
      {children}
    </SafeAreaView>
  );
}

export function ScrollScreen({
  children,
  contentContainerStyle,
  edges,
  keyboardShouldPersistTaps = 'handled',
  refreshControl,
  style,
}: ScrollScreenProps) {
  return (
    <Screen edges={edges} style={style}>
      <ScrollView
        contentContainerStyle={[styles.scrollContent, contentContainerStyle]}
        keyboardShouldPersistTaps={keyboardShouldPersistTaps}
        refreshControl={refreshControl}
        showsVerticalScrollIndicator={false}>
        {children}
      </ScrollView>
    </Screen>
  );
}

export function KeyboardScreen({
  children,
  contentContainerStyle,
  edges,
  keyboardShouldPersistTaps,
  style,
}: ScrollScreenProps) {
  return (
    <Screen edges={edges} style={style}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.flex}>
        <ScrollView
          contentContainerStyle={[styles.scrollContent, contentContainerStyle]}
          keyboardShouldPersistTaps={keyboardShouldPersistTaps ?? 'handled'}
          showsVerticalScrollIndicator={false}>
          {children}
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}

export function ScreenContent({ children, style }: PropsWithChildren<{ style?: ViewStyle }>) {
  return <View style={[styles.content, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  content: {
    flex: 1,
    paddingVertical: spacing.xl,
  },
  flex: { flex: 1 },
  screen: {
    flex: 1,
    paddingHorizontal: spacing.xl,
  },
  scrollContent: {
    flexGrow: 1,
    paddingVertical: spacing.xl,
  },
});

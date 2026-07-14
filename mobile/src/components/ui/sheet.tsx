import type { PropsWithChildren } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/ui/app-text';
import { radii, spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type SheetProps = PropsWithChildren<{
  onDismiss: () => void;
  title: string;
  visible: boolean;
}>;

export function Sheet({ children, onDismiss, title, visible }: SheetProps) {
  const insets = useSafeAreaInsets();
  const theme = useAppTheme();
  const closeLabel = title === 'Account' ? 'Close account menu' : `Close ${title.toLowerCase()}`;
  return (
    <Modal
      animationType="slide"
      onRequestClose={onDismiss}
      statusBarTranslucent
      transparent
      visible={visible}>
      <View style={[styles.backdrop, { backgroundColor: theme.colors.overlay }]}>
        <Pressable
          accessibilityLabel={closeLabel}
          accessibilityRole="button"
          onPress={onDismiss}
          style={StyleSheet.absoluteFill}
        />
        <View
          accessibilityViewIsModal
          style={[
            styles.sheet,
            { backgroundColor: theme.colors.surface, paddingBottom: Math.max(insets.bottom, spacing.lg) },
          ]}>
          <View style={styles.handle} />
          <View style={styles.header}>
            <AppText accessibilityRole="header" variant="heading">{title}</AppText>
            <Pressable
              accessibilityLabel={closeLabel}
              accessibilityRole="button"
              hitSlop={8}
              onPress={onDismiss}
              style={styles.close}>
              <AppText style={{ color: theme.colors.primaryText }} variant="bodySmall">Close</AppText>
            </Pressable>
          </View>
          <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
            {children}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  close: { alignItems: 'center', justifyContent: 'center', minHeight: 44, minWidth: 44 },
  content: { paddingHorizontal: spacing.xl, paddingVertical: spacing.lg },
  handle: { alignSelf: 'center', backgroundColor: '#94A3B8', borderRadius: 2, height: 4, marginTop: spacing.sm, width: 40 },
  header: { alignItems: 'center', flexDirection: 'row', gap: spacing.md, justifyContent: 'space-between', paddingHorizontal: spacing.xl },
  sheet: { borderTopLeftRadius: radii.lg, borderTopRightRadius: radii.lg, maxHeight: '86%', minHeight: 260 },
});

import type { PropsWithChildren, ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { Card } from '@/components/ui/card';
import { spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type DialogProps = PropsWithChildren<{
  actions?: ReactNode;
  description?: string;
  onDismiss: () => void;
  title: string;
  visible: boolean;
}>;

export function Dialog({ actions, children, description, onDismiss, title, visible }: DialogProps) {
  const theme = useAppTheme();
  return (
    <Modal
      animationType="fade"
      onRequestClose={onDismiss}
      statusBarTranslucent
      transparent
      visible={visible}>
      <View style={[styles.backdrop, { backgroundColor: theme.colors.overlay }]}>
        <Pressable
          accessibilityLabel="Dismiss dialog"
          accessibilityRole="button"
          onPress={onDismiss}
          style={StyleSheet.absoluteFill}
        />
        <Card accessibilityViewIsModal elevated style={styles.dialog}>
          <View style={styles.copy}>
            <AppText accessibilityRole="header" variant="heading">{title}</AppText>
            {description ? <AppText muted>{description}</AppText> : null}
          </View>
          {children}
          {actions ? <View style={styles.actions}>{actions}</View> : null}
        </Card>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, justifyContent: 'flex-end' },
  backdrop: { alignItems: 'center', flex: 1, justifyContent: 'center', padding: spacing.xl },
  copy: { gap: spacing.sm },
  dialog: { gap: spacing.lg, maxWidth: 480, width: '100%' },
});

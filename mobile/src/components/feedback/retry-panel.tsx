import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { spacing } from '@/constants/theme';

export function RetryPanel({
  description = 'Something went wrong. Try again.',
  onRetry,
  retryLabel = 'Try again',
  title = 'Unable to load',
}: {
  description?: string;
  onRetry: () => void;
  retryLabel?: string;
  title?: string;
}) {
  return (
    <Card style={styles.panel}>
      <View style={styles.copy}>
        <AppText style={styles.title}>{title}</AppText>
        <AppText muted variant="bodySmall">{description}</AppText>
      </View>
      <Button accessibilityLabel={retryLabel} onPress={onRetry} style={styles.button} variant="secondary">
        {retryLabel}
      </Button>
    </Card>
  );
}

const styles = StyleSheet.create({
  button: { alignSelf: 'flex-start' },
  copy: { flex: 1, gap: spacing.xs, minWidth: 0 },
  panel: { alignItems: 'flex-start', flexDirection: 'row', flexWrap: 'wrap', gap: spacing.lg },
  title: { fontWeight: '700' },
});

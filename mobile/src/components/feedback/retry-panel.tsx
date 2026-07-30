import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { spacing } from '@/constants/theme';
import { useTranslation } from 'react-i18next';

export function RetryPanel({
  description,
  onRetry,
  retryLabel,
  title,
}: {
  description?: string;
  onRetry: () => void;
  retryLabel?: string;
  title?: string;
}) {
  const {t}=useTranslation();
  const action=retryLabel??t('common.tryAgain');
  return (
    <Card style={styles.panel}>
      <View style={styles.copy}>
        <AppText style={styles.title}>{title??t('feedback.retryTitle')}</AppText>
        <AppText muted variant="bodySmall">{description??t('feedback.retryDescription')}</AppText>
      </View>
      <Button accessibilityLabel={action} onPress={onRetry} style={styles.button} variant="secondary">
        {action}
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

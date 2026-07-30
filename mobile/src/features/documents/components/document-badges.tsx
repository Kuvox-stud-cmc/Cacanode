import { View, StyleSheet } from 'react-native';

import { Badge } from '@/components/ui/badge';
import { spacing } from '@/constants/theme';
import type { DocumentStatus, DocumentVisibility } from '@/features/documents/types';
import { useTranslation } from 'react-i18next';

export function DocumentBadges({
  status,
  visibility,
}: {
  status: DocumentStatus;
  visibility: DocumentVisibility;
}) {
  const {t}=useTranslation();
  const tone = status === 'COMPLETED'
    ? 'success'
    : status === 'FAILED'
      ? 'danger'
      : status === 'PROCESSING'
        ? 'primary'
        : 'warning';
  return (
    <View style={styles.row}>
      <Badge tone={tone}>{t(`dashboard.status.${status.toLowerCase()}`)}</Badge>
      <Badge>{visibility === 'EMPLOYEE_ONLY' ? t('documents.employees') : t('documents.customersEmployees')}</Badge>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});

import { View, StyleSheet } from 'react-native';

import { Badge } from '@/components/ui/badge';
import { spacing } from '@/constants/theme';
import type { DocumentStatus, DocumentVisibility } from '@/features/documents/types';

export function DocumentBadges({
  status,
  visibility,
}: {
  status: DocumentStatus;
  visibility: DocumentVisibility;
}) {
  const tone = status === 'COMPLETED'
    ? 'success'
    : status === 'FAILED'
      ? 'danger'
      : status === 'PROCESSING'
        ? 'primary'
        : 'warning';
  return (
    <View style={styles.row}>
      <Badge tone={tone}>{status === 'COMPLETED' ? 'Ready' : titleCase(status)}</Badge>
      <Badge>{visibility === 'EMPLOYEE_ONLY' ? 'Employees' : 'Customers + employees'}</Badge>
    </View>
  );
}

function titleCase(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});

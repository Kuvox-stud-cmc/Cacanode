import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { Separator } from '@/components/ui/separator';
import { Sheet } from '@/components/ui/sheet';
import { spacing } from '@/constants/theme';
import type { ChatCitation } from '@/features/chat/types';
import { useTranslation } from 'react-i18next';

export function CitationDetailSheet({
  citation,
  onDismiss,
}: {
  citation: ChatCitation | null;
  onDismiss: () => void;
}) {
  const {t}=useTranslation();
  if (!citation) return null;

  const metadata = [
    [t('chat.page'), citation.pageNumber?.toString()],
    [t('chat.section'), citation.sectionPath.length ? citation.sectionPath.join(' › ') : null],
    [t('chat.sheet'), citation.sheetName],
    [t('chat.cells'), citation.cellRange],
    [t('chat.blockType'), citation.blockType],
    [t('chat.modality'), citation.modality],
    [t('chat.unit'), citation.unitId],
    [t('chat.table'), citation.tableId],
  ].filter((item): item is [string, string] => Boolean(item[1]));

  return (
    <Sheet onDismiss={onDismiss} title={t('chat.citationDetails')} visible>
      <View style={styles.content}>
        <View style={styles.section}>
          <AppText muted variant="bodySmall">{t('chat.source')}</AppText>
          <AppText accessibilityRole="header" variant="heading">{citation.sourceName}</AppText>
        </View>
        <Separator />
        <View style={styles.section}>
          <AppText muted variant="bodySmall">{t('chat.snippet')}</AppText>
          <AppText>{citation.snippet}</AppText>
        </View>
        {metadata.length ? <Separator /> : null}
        {metadata.map(([label, value]) => (
          <View key={label} style={styles.metadataRow}>
            <AppText muted style={styles.metadataLabel} variant="bodySmall">{label}</AppText>
            <AppText style={styles.metadataValue} variant="bodySmall">{value}</AppText>
          </View>
        ))}
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg },
  metadataLabel: { flexBasis: 96, fontWeight: '600' },
  metadataRow: { alignItems: 'flex-start', flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  metadataValue: { flex: 1, minWidth: 160 },
  section: { gap: spacing.sm },
});

import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { Separator } from '@/components/ui/separator';
import { Sheet } from '@/components/ui/sheet';
import { spacing } from '@/constants/theme';
import type { ChatCitation } from '@/features/chat/types';

export function CitationDetailSheet({
  citation,
  onDismiss,
}: {
  citation: ChatCitation | null;
  onDismiss: () => void;
}) {
  if (!citation) return null;

  const metadata = [
    ['Page', citation.pageNumber?.toString()],
    ['Section', citation.sectionPath.length ? citation.sectionPath.join(' › ') : null],
    ['Sheet', citation.sheetName],
    ['Cells', citation.cellRange],
    ['Block type', citation.blockType],
    ['Modality', citation.modality],
    ['Unit', citation.unitId],
    ['Table', citation.tableId],
  ].filter((item): item is [string, string] => Boolean(item[1]));

  return (
    <Sheet onDismiss={onDismiss} title="Citation details" visible>
      <View style={styles.content}>
        <View style={styles.section}>
          <AppText muted variant="bodySmall">Source</AppText>
          <AppText accessibilityRole="header" variant="heading">{citation.sourceName}</AppText>
        </View>
        <Separator />
        <View style={styles.section}>
          <AppText muted variant="bodySmall">Snippet</AppText>
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

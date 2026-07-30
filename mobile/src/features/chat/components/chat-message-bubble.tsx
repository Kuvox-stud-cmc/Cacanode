import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { radii, spacing } from '@/constants/theme';
import type { ChatCitation, TranscriptMessage } from '@/features/chat/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useTranslation } from 'react-i18next';

export function ChatMessageBubble({
  message,
  onOpenCitation,
  onReload,
  reloadDisabled,
}: {
  message: TranscriptMessage;
  onOpenCitation: (citation: ChatCitation) => void;
  onReload: () => void;
  reloadDisabled: boolean;
}) {
  const theme = useAppTheme();
  const {t}=useTranslation();
  const isUser = message.role === 'user';

  return (
    <View
      accessibilityLabel={`${isUser ? t('chat.you') : t('chat.assistant')}: ${message.content}`}
      style={[styles.row, isUser ? styles.userRow : styles.assistantRow]}>
      <View
        style={[
          styles.bubble,
          {
            backgroundColor: isUser ? theme.colors.primary : theme.colors.surface,
            borderColor: isUser ? theme.colors.primary : theme.colors.border,
          },
        ]}>
        {message.status === 'pending' ? (
          <View accessibilityLiveRegion="polite" accessibilityRole="progressbar" style={styles.pending}>
            <ActivityIndicator color={theme.colors.primaryText} />
            <AppText muted>{t('chat.thinking')}</AppText>
          </View>
        ) : (
          <AppText style={isUser ? styles.userText : undefined}>
            {message.status === 'failed' ? t('chat.failed') : message.content || t('chat.noAnswer')}
          </AppText>
        )}

        {message.noInformation ? (
          <AppText muted style={styles.explanation} variant="bodySmall">
            {t('chat.noInformation')}
          </AppText>
        ) : null}

        {message.status === 'failed' ? (
          <View style={styles.failure}>
            <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }} variant="bodySmall">
              {message.failureMessage}
            </AppText>
            <Pressable
              accessibilityLabel={t('chat.reload')}
              accessibilityRole="button"
              accessibilityState={{ disabled: reloadDisabled }}
              disabled={reloadDisabled}
              onPress={onReload}
              style={styles.reload}>
              <AppText style={{ color: theme.colors.primaryText }} variant="bodySmall">
                {t('chat.reload')}
              </AppText>
            </Pressable>
          </View>
        ) : null}

        {message.role === 'assistant' && message.citations.length ? (
          <View accessibilityLabel={t('chat.sources')} style={styles.citations}>
            {message.citations.map((citation, index) => (
              <Pressable
                accessibilityLabel={t('chat.openCitation',{number:index+1,name:citation.sourceName})}
                accessibilityRole="button"
                key={`${message.id}-${citation.id}`}
                onPress={() => onOpenCitation(citation)}
                style={[styles.citation, { borderColor: theme.colors.border }]}>
                <AppText style={{ color: theme.colors.primaryText }} variant="bodySmall">
                  {index + 1}. {citation.sourceName}
                </AppText>
              </Pressable>
            ))}
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  assistantRow: { justifyContent: 'flex-start' },
  bubble: { borderRadius: radii.lg, borderWidth: 1, gap: spacing.md, maxWidth: '88%', padding: spacing.lg },
  citation: { borderRadius: radii.md, borderWidth: 1, justifyContent: 'center', minHeight: 44, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  citations: { gap: spacing.sm },
  explanation: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: spacing.md },
  failure: { gap: spacing.sm },
  pending: { alignItems: 'center', flexDirection: 'row', gap: spacing.sm, minHeight: 24 },
  reload: { alignSelf: 'flex-start', justifyContent: 'center', minHeight: 44 },
  row: { flexDirection: 'row', paddingHorizontal: spacing.xl },
  userRow: { justifyContent: 'flex-end' },
  userText: { color: '#FFFFFF' },
});

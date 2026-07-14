import { Fragment } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/feedback/empty-state';
import { LoadingState } from '@/components/feedback/loading-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { AppText } from '@/components/ui/app-text';
import { Separator } from '@/components/ui/separator';
import { Sheet } from '@/components/ui/sheet';
import { radii, spacing } from '@/constants/theme';
import type { PlaygroundSession } from '@/features/chat/types';
import { useAppTheme } from '@/hooks/use-app-theme';

export function ChatHistorySheet({
  activeSessionId,
  error,
  hideError,
  hiding,
  loading,
  onDismiss,
  onHide,
  onRetry,
  onSelect,
  sessions,
  visible,
}: {
  activeSessionId: string | null;
  error: string | null;
  hideError: string | null;
  hiding: boolean;
  loading: boolean;
  onDismiss: () => void;
  onHide: (session: PlaygroundSession) => void;
  onRetry: () => void;
  onSelect: (session: PlaygroundSession) => void;
  sessions: PlaygroundSession[];
  visible: boolean;
}) {
  const theme = useAppTheme();
  return (
    <Sheet onDismiss={onDismiss} title="Conversation history" visible={visible}>
      {loading && sessions.length === 0 ? (
        <LoadingState description="Loading your latest employee conversations." title="Loading history" />
      ) : error && sessions.length === 0 ? (
        <RetryPanel description={error} onRetry={onRetry} title="Unable to load history" />
      ) : sessions.length === 0 ? (
        <EmptyState
          description="Your conversations will appear here after you send a question."
          title="No conversation history"
        />
      ) : (
        <View style={styles.list}>
          {error ? (
            <RetryPanel description={error} onRetry={onRetry} title="History may be out of date" />
          ) : null}
          {hideError ? (
            <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }}>
              {hideError}
            </AppText>
          ) : null}
          {sessions.map((session, index) => (
            <Fragment key={session.id}>
              {index > 0 ? <Separator /> : null}
              <HistoryRow
                active={session.id === activeSessionId}
                disabled={hiding}
                onHide={() => onHide(session)}
                onSelect={() => onSelect(session)}
                session={session}
              />
            </Fragment>
          ))}
        </View>
      )}
    </Sheet>
  );
}

function HistoryRow({
  active,
  disabled,
  onHide,
  onSelect,
  session,
}: {
  active: boolean;
  disabled: boolean;
  onHide: () => void;
  onSelect: () => void;
  session: PlaygroundSession;
}) {
  const theme = useAppTheme();
  return (
    <View style={styles.row}>
      <Pressable
        accessibilityLabel={session.title}
        accessibilityRole="button"
        accessibilityState={{ selected: active }}
        disabled={disabled}
        onPress={onSelect}
        style={({ pressed }) => [
          styles.select,
          { backgroundColor: active || pressed ? theme.colors.primarySoft : 'transparent' },
        ]}>
        <AppText numberOfLines={2} style={styles.title}>{session.title}</AppText>
        <AppText muted variant="caption">
          {session.messageCount} messages · {formatSessionDate(session.lastActivityAt)}
        </AppText>
      </Pressable>
      <Pressable
        accessibilityHint="Hides this conversation from your history"
        accessibilityLabel={`Hide ${session.title}`}
        accessibilityRole="button"
        disabled={disabled}
        hitSlop={4}
        onPress={onHide}
        style={styles.hide}>
        <AppText style={{ color: theme.colors.dangerText }} variant="bodySmall">Hide</AppText>
      </Pressable>
    </View>
  );
}

function formatSessionDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Recent';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date);
}

const styles = StyleSheet.create({
  hide: { alignItems: 'center', justifyContent: 'center', minHeight: 44, minWidth: 52 },
  list: { gap: spacing.md },
  row: { alignItems: 'center', flexDirection: 'row', gap: spacing.sm },
  select: { borderRadius: radii.md, flex: 1, gap: spacing.xs, minHeight: 60, padding: spacing.md },
  title: { fontWeight: '600' },
});

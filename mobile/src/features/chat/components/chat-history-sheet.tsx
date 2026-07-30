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
import { useTranslation } from 'react-i18next';

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
  const {t}=useTranslation();
  return (
    <Sheet onDismiss={onDismiss} title={t('chat.historyTitle')} visible={visible}>
      {loading && sessions.length === 0 ? (
        <LoadingState description={t('chat.loadingHistoryDescription')} title={t('chat.loadingHistory')} />
      ) : error && sessions.length === 0 ? (
        <RetryPanel description={error} onRetry={onRetry} title={t('chat.unableHistory')} />
      ) : sessions.length === 0 ? (
        <EmptyState
          description={t('chat.noHistoryDescription')}
          title={t('chat.noHistory')}
        />
      ) : (
        <View style={styles.list}>
          {error ? (
            <RetryPanel description={error} onRetry={onRetry} title={t('chat.staleHistory')} />
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
  const {t,i18n}=useTranslation();const locale=i18n.resolvedLanguage?.startsWith('vi')?'vi-VN':'en-US';
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
          {t('chat.messageCount',{count:session.messageCount})} · {formatSessionDate(session.lastActivityAt,locale,t('chat.recent'))}
        </AppText>
      </Pressable>
      <Pressable
        accessibilityHint={t('chat.hideHint')}
        accessibilityLabel={`${t('chat.hideAction')} ${session.title}`}
        accessibilityRole="button"
        disabled={disabled}
        hitSlop={4}
        onPress={onHide}
        style={styles.hide}>
        <AppText style={{ color: theme.colors.dangerText }} variant="bodySmall">{t('chat.hideAction')}</AppText>
      </Pressable>
    </View>
  );
}

function formatSessionDate(value:string,locale:string,recent:string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return recent;
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date);
}

const styles = StyleSheet.create({
  hide: { alignItems: 'center', justifyContent: 'center', minHeight: 44, minWidth: 52 },
  list: { gap: spacing.md },
  row: { alignItems: 'center', flexDirection: 'row', gap: spacing.sm },
  select: { borderRadius: radii.md, flex: 1, gap: spacing.xs, minHeight: 60, padding: spacing.md },
  title: { fontWeight: '600' },
});

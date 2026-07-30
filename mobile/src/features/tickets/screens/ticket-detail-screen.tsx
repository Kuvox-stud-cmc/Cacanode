import { router, useLocalSearchParams, type Href } from 'expo-router';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/feedback/empty-state';
import { LoadingState } from '@/components/feedback/loading-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { Screen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Separator } from '@/components/ui/separator';
import { Sheet } from '@/components/ui/sheet';
import { TextField } from '@/components/ui/text-field';
import { radii, spacing } from '@/constants/theme';
import {
  useAddTicketNoteMutation,
  useGetTicketQuery,
  useListTicketAssigneesQuery,
  useUpdateTicketMutation,
} from '@/features/tickets/api/tickets-api';
import {
  ticketFiltersFromRoute,
  ticketFiltersToRoute,
} from '@/features/tickets/model/ticket-state';
import {
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  type TicketNote,
  type TicketStatus,
  type TicketUpdate,
} from '@/features/tickets/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import type { ApiError } from '@/services/api/errors';

export function TicketDetailScreen() {
  const { i18n, t } = useTranslation();
  const isVietnamese = i18n.resolvedLanguage?.startsWith('vi') ?? false;
  const locale = isVietnamese ? 'vi-VN' : 'en-US';
  const theme = useAppTheme();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const ticketId = first(params.ticketId) ?? '';
  const filters = useMemo(() => ticketFiltersFromRoute(params), [params]);
  const query = useGetTicketQuery(ticketId, { skip: !ticketId });
  const assigneesQuery = useListTicketAssigneesQuery();
  const [updateTicket, updateState] = useUpdateTicketMutation();
  const [addNote, noteState] = useAddTicketNoteMutation();
  const [statusSheetVisible, setStatusSheetVisible] = useState(false);
  const [prioritySheetVisible, setPrioritySheetVisible] = useState(false);
  const [assigneeSheetVisible, setAssigneeSheetVisible] = useState(false);
  const [pendingTerminalStatus, setPendingTerminalStatus] = useState<TicketStatus | null>(null);
  const [noteDraft, setNoteDraft] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const ticket = query.data;
  const statusLabel = (status: TicketStatus) => t(`tickets.statusLabels.${statusKey(status)}`);
  const priorityLabel = (priority: typeof TICKET_PRIORITIES[number]) => t(`tickets.priorityLabels.${priority.toLowerCase() as Lowercase<typeof priority>}`);

  function backToList() {
    router.replace({ pathname: '/tickets', params: ticketFiltersToRoute(filters) } as unknown as Href);
  }

  async function applyUpdate(update: TicketUpdate, successMessage: string) {
    if (!ticket || updateState.isLoading) return;
    setActionError(null);
    setConfirmation(null);
    setStatusSheetVisible(false);
    setPrioritySheetVisible(false);
    setAssigneeSheetVisible(false);
    try {
      await updateTicket({ ticketId: ticket.id, update }).unwrap();
      setPendingTerminalStatus(null);
      setConfirmation(successMessage);
      await query.refetch();
    } catch (error) {
      setActionError(apiMessage(error, t('tickets.updateError'), isVietnamese));
      await query.refetch();
      if ((error as ApiError | undefined)?.status === 400) await assigneesQuery.refetch();
    }
  }

  function selectStatus(status: TicketStatus) {
    if (!ticket || status === ticket.status) {
      setStatusSheetVisible(false);
      return;
    }
    if (status === 'RESOLVED' || status === 'CLOSED') {
      setStatusSheetVisible(false);
      setPendingTerminalStatus(status);
      return;
    }
    void applyUpdate({ status }, t('tickets.statusChanged', { status: statusLabel(status) }));
  }

  async function submitNote() {
    if (!ticket || noteState.isLoading) return;
    const validation = validateNote(noteDraft, t('tickets.noteRequired'), t('tickets.noteTooLong'));
    if (validation) {
      setNoteError(validation);
      return;
    }
    setNoteError(null);
    setActionError(null);
    try {
      await addNote({ ticketId: ticket.id, content: noteDraft.trim() }).unwrap();
      setNoteDraft('');
      setConfirmation(t('tickets.noteAdded'));
      await query.refetch();
    } catch (error) {
      setNoteError(apiMessage(error, t('tickets.noteError'), isVietnamese));
    }
  }

  function openConversation() {
    if (!ticket) return;
    router.push({
      pathname: '/conversations/[conversationId]',
      params: {
        conversationId: ticket.sessionId,
        returnTicketId: ticket.id,
        returnTicketStatus: filters.status,
        returnTicketPriority: filters.priority,
        returnTicketSource: filters.source,
        returnTicketAssignee: filters.assignee,
      },
    } as unknown as Href);
  }

  if (query.isLoading) return <LoadingState description={t('tickets.detailsLoadingDescription')} title={t('tickets.detailsLoading')} />;
  if (query.isError || !ticket) {
    const unavailable = (query.error as ApiError | undefined)?.status === 404;
    return (
      <Screen edges={['right', 'bottom', 'left']} style={styles.unavailableScreen}>
        <View style={styles.unavailableContent}>
          <RetryPanel
            description={unavailable
              ? t('tickets.gone')
              : t('tickets.detailError')}
            onRetry={() => void query.refetch()}
            title={t('tickets.unavailable')}
          />
          <Button onPress={backToList} variant="secondary">{t('tickets.back')}</Button>
        </View>
      </Screen>
    );
  }

  const controlsDisabled = updateState.isLoading;
  return (
    <Screen edges={['right', 'bottom', 'left']} style={styles.screen}>
      <FlatList
        contentContainerStyle={styles.listContent}
        data={ticket.notes}
        keyExtractor={(note) => note.id}
        keyboardShouldPersistTaps="handled"
        testID="ticket-notes"
        ListHeaderComponent={(
          <View style={styles.header}>
            <View style={styles.heading}>
              <AppText accessibilityRole="header" variant="title">{ticket.title}</AppText>
              <View style={styles.badges}>
                <Badge>{statusLabel(ticket.status)}</Badge>
                <Badge tone="warning">{priorityLabel(ticket.priority)}</Badge>
                <Badge tone="primary">{t(`tickets.sourceLabels.${ticket.source === 'CUSTOM_API' ? 'customApi' : 'widget'}`)}</Badge>
              </View>
            </View>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>{t('tickets.customer')}</AppText>
              <DetailRow label={t('tickets.identity')} value={customerIdentity(ticket, t('tickets.customer'))} />
              <DetailRow label={t('tickets.name')} value={available(ticket.customerName, t('common.unavailable'))} />
              <DetailRow label={t('tickets.email')} value={available(ticket.customerEmail, t('common.unavailable'))} />
              <DetailRow label={t('tickets.externalId')} value={available(ticket.externalUserId, t('common.unavailable'))} />
            </Card>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>{t('tickets.ticketDescription')}</AppText>
              <AppText>{ticket.description}</AppText>
            </Card>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>{t('tickets.details')}</AppText>
              <DetailRow label={t('tickets.assignee')} value={ticket.assignedToName || t('tickets.unassigned')} />
              <DetailRow label={t('tickets.created')} value={formatDate(ticket.createdAt, locale, t('common.unavailable'))} />
              <DetailRow label={t('tickets.updated')} value={formatDate(ticket.updatedAt, locale, t('common.unavailable'))} />
              <DetailRow label={t('tickets.resolved')} value={ticket.resolvedAt ? formatDate(ticket.resolvedAt, locale, t('common.unavailable')) : t('tickets.notResolved')} />
              <DetailRow label={t('tickets.conversation')} value={ticket.sessionId} />
              <Button onPress={openConversation} variant="secondary">{t('tickets.openConversation')}</Button>
            </Card>

            {confirmation ? <AppText accessibilityRole="alert" style={{ color: theme.colors.successText }}>{confirmation}</AppText> : null}
            {actionError ? <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }}>{actionError}</AppText> : null}

            <View style={styles.actions}>
              <Button disabled={controlsDisabled} onPress={() => setStatusSheetVisible(true)} variant="secondary">
                {t('tickets.changeStatus')}
              </Button>
              <Button disabled={controlsDisabled} onPress={() => setPrioritySheetVisible(true)} variant="secondary">
                {t('tickets.changePriority')}
              </Button>
              <Button disabled={controlsDisabled} onPress={() => setAssigneeSheetVisible(true)} variant="secondary">
                {t('tickets.changeAssignee')}
              </Button>
            </View>

            <Separator />
            <View style={styles.noteComposer}>
              <AppText accessibilityRole="header" variant="heading">{t('tickets.notes')}</AppText>
              <TextField
                error={noteError ?? undefined}
                label={t('tickets.newNote')}
                maxLength={5000}
                multiline
                onChangeText={(value) => {
                  setNoteDraft(value);
                  if (noteError) setNoteError(null);
                }}
                placeholder={t('tickets.notePlaceholder')}
                style={styles.noteInput}
                textAlignVertical="top"
                value={noteDraft}
              />
              <Button loading={noteState.isLoading} onPress={() => void submitNote()}>{t('tickets.addNote')}</Button>
            </View>
          </View>
        )}
        ListEmptyComponent={<EmptyState description={t('tickets.noNotesDescription')} title={t('tickets.noNotes')} />}
        refreshControl={(
          <RefreshControl
            onRefresh={() => {
              void query.refetch();
              void assigneesQuery.refetch();
            }}
            refreshing={query.isFetching}
            tintColor={theme.colors.primary}
          />
        )}
        renderItem={({ item }) => <NoteCard locale={locale} note={item} unavailable={t('common.unavailable')} />}
        showsVerticalScrollIndicator={false}
      />

      <ChoiceSheet
        onDismiss={() => setStatusSheetVisible(false)}
        onSelect={(value) => selectStatus(value as TicketStatus)}
        options={TICKET_STATUSES.map((status) => ({ label: statusLabel(status), value: status }))}
        selected={ticket.status}
        title={t('tickets.statusSheet')}
        visible={statusSheetVisible}
      />
      <ChoiceSheet
        onDismiss={() => setPrioritySheetVisible(false)}
        onSelect={(value) => void applyUpdate({ priority: value as typeof ticket.priority }, t('tickets.priorityChanged', { priority: priorityLabel(value as typeof ticket.priority) }))}
        options={TICKET_PRIORITIES.map((priority) => ({ label: priorityLabel(priority), value: priority }))}
        selected={ticket.priority}
        title={t('tickets.prioritySheet')}
        visible={prioritySheetVisible}
      />
      <Sheet onDismiss={() => setAssigneeSheetVisible(false)} title={t('tickets.assigneeSheet')} visible={assigneeSheetVisible}>
        {assigneesQuery.isError ? (
          <RetryPanel description={t('tickets.assigneesError')} onRetry={() => void assigneesQuery.refetch()} title={t('tickets.assigneesUnavailable')} />
        ) : (
          <View style={styles.choiceColumn}>
            <ChoiceButton
              label={t('tickets.unassigned')}
              onPress={() => void applyUpdate({ clearAssignee: true }, t('tickets.nowUnassigned'))}
              selected={!ticket.assignedTo}
            />
            {(assigneesQuery.data ?? []).map((assignee) => (
              <ChoiceButton
                key={assignee.id}
                label={assignee.fullName || assignee.email}
                onPress={() => void applyUpdate({ assignedTo: assignee.id }, t('tickets.assigneeChanged', { name: assignee.fullName || assignee.email }))}
                selected={ticket.assignedTo === assignee.id}
              />
            ))}
          </View>
        )}
      </Sheet>

      <Dialog
        actions={(
          <>
            <Button onPress={() => setPendingTerminalStatus(null)} variant="secondary">{t('common.cancel')}</Button>
            <Button
              loading={updateState.isLoading}
              onPress={() => pendingTerminalStatus && void applyUpdate(
                { status: pendingTerminalStatus },
                t('tickets.statusChanged', { status: statusLabel(pendingTerminalStatus) }),
              )}
              variant={pendingTerminalStatus === 'CLOSED' ? 'danger' : 'primary'}>
              {t('common.confirm')}
            </Button>
          </>
        )}
        description={pendingTerminalStatus === 'CLOSED'
          ? t('tickets.closeDescription')
          : t('tickets.resolveDescription')}
        onDismiss={() => setPendingTerminalStatus(null)}
        title={pendingTerminalStatus === 'CLOSED' ? t('tickets.closeTitle') : t('tickets.resolveTitle')}
        visible={Boolean(pendingTerminalStatus)}
      />
    </Screen>
  );
}

function ChoiceSheet({ onDismiss, onSelect, options, selected, title, visible }: {
  onDismiss: () => void;
  onSelect: (value: string) => void;
  options: { label: string; value: string }[];
  selected: string;
  title: string;
  visible: boolean;
}) {
  return (
    <Sheet onDismiss={onDismiss} title={title} visible={visible}>
      <View style={styles.choiceColumn}>
        {options.map((option) => (
          <ChoiceButton key={option.value} label={option.label} onPress={() => onSelect(option.value)} selected={selected === option.value} />
        ))}
      </View>
    </Sheet>
  );
}

function ChoiceButton({ label, onPress, selected }: { label: string; onPress: () => void; selected: boolean }) {
  const theme = useAppTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[styles.choiceButton, { borderColor: selected ? theme.colors.primary : theme.colors.border }]}>
      <AppText style={{ color: selected ? theme.colors.primaryText : theme.colors.text }}>{label}</AppText>
    </Pressable>
  );
}

function NoteCard({ locale, note, unavailable }: { locale: string; note: TicketNote; unavailable: string }) {
  return (
    <Card style={styles.noteCard}>
      <AppText>{note.content}</AppText>
      <AppText muted variant="caption">{note.authorName} · {formatDate(note.createdAt, locale, unavailable)}</AppText>
    </Card>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailRow}>
      <AppText muted style={styles.detailLabel} variant="bodySmall">{label}</AppText>
      <AppText selectable style={styles.detailValue} variant="bodySmall">{value}</AppText>
    </View>
  );
}

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function statusKey(status: TicketStatus): 'open' | 'inProgress' | 'resolved' | 'closed' {
  if (status === 'IN_PROGRESS') return 'inProgress';
  return status.toLowerCase() as 'open' | 'resolved' | 'closed';
}

function customerIdentity(ticket: { customerName: string | null; customerEmail: string | null; externalUserId: string | null }, fallback: string) {
  return ticket.customerName?.trim()
    || ticket.customerEmail?.trim()
    || ticket.externalUserId?.trim()
    || fallback;
}

function validateNote(value: string, required: string, tooLong: string) {
  const trimmed = value.trim();
  if (!trimmed) return required;
  if (trimmed.length > 5000) return tooLong;
  return null;
}

function available(value: string | null, fallback: string) {
  return value?.trim() || fallback;
}

function formatDate(value: string, locale: string, unavailable: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? unavailable : date.toLocaleString(locale);
}

function apiMessage(error: unknown, fallback: string, forceFallback: boolean) {
  return !forceFallback && typeof error === 'object' && error !== null && 'message' in error
    && typeof error.message === 'string' ? error.message : fallback;
}

const styles = StyleSheet.create({
  actions: { gap: spacing.md },
  badges: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  card: { gap: spacing.md },
  choiceButton: { borderRadius: radii.md, borderWidth: 1, minHeight: 48, paddingHorizontal: spacing.lg, paddingVertical: spacing.md },
  choiceColumn: { gap: spacing.sm },
  detailLabel: { flexBasis: 100, fontWeight: '600' },
  detailRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md },
  detailValue: { flex: 1 },
  header: { gap: spacing.lg, paddingBottom: spacing.lg },
  heading: { gap: spacing.md },
  listContent: { paddingVertical: spacing.xl },
  noteCard: { gap: spacing.md, marginBottom: spacing.md },
  noteComposer: { gap: spacing.md },
  noteInput: { minHeight: 120 },
  screen: { paddingHorizontal: spacing.xl },
  sectionTitle: { fontWeight: '700' },
  unavailableContent: { flex: 1, gap: spacing.lg, justifyContent: 'center' },
  unavailableScreen: { paddingHorizontal: spacing.xl },
});

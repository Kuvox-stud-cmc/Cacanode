import { router, useLocalSearchParams, type Href } from 'expo-router';
import { useMemo, useState } from 'react';
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
  ticketCustomerIdentity,
  ticketFiltersFromRoute,
  ticketFiltersToRoute,
  ticketSourceLabel,
  ticketStatusLabel,
  validateTicketNote,
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
      setActionError(apiMessage(error, 'The ticket could not be updated.'));
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
    void applyUpdate({ status }, `Status changed to ${ticketStatusLabel(status)}.`);
  }

  async function submitNote() {
    if (!ticket || noteState.isLoading) return;
    const validation = validateTicketNote(noteDraft);
    if (validation) {
      setNoteError(validation);
      return;
    }
    setNoteError(null);
    setActionError(null);
    try {
      await addNote({ ticketId: ticket.id, content: noteDraft.trim() }).unwrap();
      setNoteDraft('');
      setConfirmation('Internal note added.');
      await query.refetch();
    } catch (error) {
      setNoteError(apiMessage(error, 'The note could not be added. Your draft has been kept.'));
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

  if (query.isLoading) return <LoadingState description="Loading ticket details and notes." title="Loading ticket" />;
  if (query.isError || !ticket) {
    const unavailable = (query.error as ApiError | undefined)?.status === 404;
    return (
      <Screen edges={['right', 'bottom', 'left']} style={styles.unavailableScreen}>
        <View style={styles.unavailableContent}>
          <RetryPanel
            description={unavailable
              ? 'This ticket is no longer available or belongs to another workspace.'
              : 'Ticket details could not be loaded.'}
            onRetry={() => void query.refetch()}
            title="Ticket unavailable"
          />
          <Button onPress={backToList} variant="secondary">Back to Tickets</Button>
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
                <Badge>{ticketStatusLabel(ticket.status)}</Badge>
                <Badge tone="warning">{ticket.priority.toLowerCase()}</Badge>
                <Badge tone="primary">{ticketSourceLabel(ticket.source)}</Badge>
              </View>
            </View>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>Customer</AppText>
              <DetailRow label="Identity" value={ticketCustomerIdentity(ticket)} />
              <DetailRow label="Name" value={available(ticket.customerName)} />
              <DetailRow label="Email" value={available(ticket.customerEmail)} />
              <DetailRow label="External ID" value={available(ticket.externalUserId)} />
            </Card>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>Description</AppText>
              <AppText>{ticket.description}</AppText>
            </Card>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>Ticket details</AppText>
              <DetailRow label="Assignee" value={ticket.assignedToName || 'Unassigned'} />
              <DetailRow label="Created" value={formatDate(ticket.createdAt)} />
              <DetailRow label="Updated" value={formatDate(ticket.updatedAt)} />
              <DetailRow label="Resolved" value={ticket.resolvedAt ? formatDate(ticket.resolvedAt) : 'Not resolved'} />
              <DetailRow label="Conversation" value={ticket.sessionId} />
              <Button onPress={openConversation} variant="secondary">Open conversation</Button>
            </Card>

            {confirmation ? <AppText accessibilityRole="alert" style={{ color: theme.colors.successText }}>{confirmation}</AppText> : null}
            {actionError ? <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }}>{actionError}</AppText> : null}

            <View style={styles.actions}>
              <Button disabled={controlsDisabled} onPress={() => setStatusSheetVisible(true)} variant="secondary">
                Change status
              </Button>
              <Button disabled={controlsDisabled} onPress={() => setPrioritySheetVisible(true)} variant="secondary">
                Change priority
              </Button>
              <Button disabled={controlsDisabled} onPress={() => setAssigneeSheetVisible(true)} variant="secondary">
                Change assignee
              </Button>
            </View>

            <Separator />
            <View style={styles.noteComposer}>
              <AppText accessibilityRole="header" variant="heading">Internal notes</AppText>
              <TextField
                error={noteError ?? undefined}
                label="New internal note"
                maxLength={5000}
                multiline
                onChangeText={(value) => {
                  setNoteDraft(value);
                  if (noteError) setNoteError(null);
                }}
                placeholder="Add context for your team"
                style={styles.noteInput}
                textAlignVertical="top"
                value={noteDraft}
              />
              <Button loading={noteState.isLoading} onPress={() => void submitNote()}>Add note</Button>
            </View>
          </View>
        )}
        ListEmptyComponent={<EmptyState description="Add a note to share internal context with your team." title="No internal notes" />}
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
        renderItem={({ item }) => <NoteCard note={item} />}
        showsVerticalScrollIndicator={false}
      />

      <ChoiceSheet
        onDismiss={() => setStatusSheetVisible(false)}
        onSelect={(value) => selectStatus(value as TicketStatus)}
        options={TICKET_STATUSES.map((status) => ({ label: ticketStatusLabel(status), value: status }))}
        selected={ticket.status}
        title="Ticket status"
        visible={statusSheetVisible}
      />
      <ChoiceSheet
        onDismiss={() => setPrioritySheetVisible(false)}
        onSelect={(value) => void applyUpdate({ priority: value as typeof ticket.priority }, `Priority changed to ${value.toLowerCase()}.`)}
        options={TICKET_PRIORITIES.map((priority) => ({ label: priority.toLowerCase(), value: priority }))}
        selected={ticket.priority}
        title="Ticket priority"
        visible={prioritySheetVisible}
      />
      <Sheet onDismiss={() => setAssigneeSheetVisible(false)} title="Ticket assignee" visible={assigneeSheetVisible}>
        {assigneesQuery.isError ? (
          <RetryPanel description="Assignees could not be loaded." onRetry={() => void assigneesQuery.refetch()} title="Assignees unavailable" />
        ) : (
          <View style={styles.choiceColumn}>
            <ChoiceButton
              label="Unassigned"
              onPress={() => void applyUpdate({ clearAssignee: true }, 'Ticket is now unassigned.')}
              selected={!ticket.assignedTo}
            />
            {(assigneesQuery.data ?? []).map((assignee) => (
              <ChoiceButton
                key={assignee.id}
                label={assignee.fullName || assignee.email}
                onPress={() => void applyUpdate({ assignedTo: assignee.id }, `Assigned to ${assignee.fullName || assignee.email}.`)}
                selected={ticket.assignedTo === assignee.id}
              />
            ))}
          </View>
        )}
      </Sheet>

      <Dialog
        actions={(
          <>
            <Button onPress={() => setPendingTerminalStatus(null)} variant="secondary">Cancel</Button>
            <Button
              loading={updateState.isLoading}
              onPress={() => pendingTerminalStatus && void applyUpdate(
                { status: pendingTerminalStatus },
                `Status changed to ${ticketStatusLabel(pendingTerminalStatus)}.`,
              )}
              variant={pendingTerminalStatus === 'CLOSED' ? 'danger' : 'primary'}>
              Confirm
            </Button>
          </>
        )}
        description={pendingTerminalStatus === 'CLOSED'
          ? 'Closing marks the ticket as complete. It can be reopened later if necessary.'
          : 'Resolving marks the customer request as handled.'}
        onDismiss={() => setPendingTerminalStatus(null)}
        title={pendingTerminalStatus === 'CLOSED' ? 'Close ticket?' : 'Resolve ticket?'}
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

function NoteCard({ note }: { note: TicketNote }) {
  return (
    <Card style={styles.noteCard}>
      <AppText>{note.content}</AppText>
      <AppText muted variant="caption">{note.authorName} · {formatDate(note.createdAt)}</AppText>
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

function available(value: string | null) {
  return value?.trim() || 'Unavailable';
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Unavailable' : date.toLocaleString();
}

function apiMessage(error: unknown, fallback: string) {
  return typeof error === 'object' && error !== null && 'message' in error
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

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
import { spacing } from '@/constants/theme';
import { CitationDetailSheet } from '@/features/chat/components/citation-detail-sheet';
import {
  useCloseConversationMutation,
  useGetConversationQuery,
} from '@/features/conversations/api/conversations-api';
import {
  chronologicalMessages,
  conversationFiltersFromRoute,
  conversationFiltersToRoute,
  customerIdentity,
  displayMetadata,
  ticketDraftFromAction,
} from '@/features/conversations/model/conversation-state';
import type {
  ConversationCitation,
  ConversationMessage,
} from '@/features/conversations/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import type { ApiError } from '@/services/api/errors';

export function ConversationDetailScreen() {
  const theme = useAppTheme();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const conversationId = first(params.conversationId) ?? '';
  const filters = useMemo(() => conversationFiltersFromRoute(params), [params]);
  const [citation, setCitation] = useState<ConversationCitation | null>(null);
  const [closeVisible, setCloseVisible] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const query = useGetConversationQuery(conversationId, { skip: !conversationId });
  const [closeConversation, closeState] = useCloseConversationMutation();
  const conversation = query.data;
  const messages = useMemo(
    () => chronologicalMessages(conversation?.messages ?? []),
    [conversation?.messages],
  );
  const metadata = useMemo(
    () => displayMetadata(conversation?.customer.metadata ?? {}),
    [conversation?.customer.metadata],
  );

  function backFromUnavailable() {
    const returnTicketId = first(params.returnTicketId);
    if (returnTicketId) {
      router.replace({
        pathname: '/tickets/[ticketId]',
        params: {
          ticketId: returnTicketId,
          status: first(params.returnTicketStatus),
          priority: first(params.returnTicketPriority),
          source: first(params.returnTicketSource),
          assignee: first(params.returnTicketAssignee),
        },
      } as unknown as Href);
      return;
    }
    router.replace({
      pathname: '/conversations',
      params: conversationFiltersToRoute(filters),
    } as unknown as Href);
  }

  async function handleClose() {
    if (!conversation || conversation.status === 'CLOSED') return;
    setActionError(null);
    setConfirmation(null);
    try {
      await closeConversation(conversation.id).unwrap();
      setCloseVisible(false);
      setConfirmation('Conversation closed.');
      await query.refetch();
    } catch (error) {
      setActionError(apiMessage(error, 'The conversation could not be closed.'));
    }
  }

  if (query.isLoading) {
    return <LoadingState description="Retrieving the customer transcript." title="Loading conversation" />;
  }

  if (query.isError || !conversation) {
    const unavailable = (query.error as ApiError | undefined)?.status === 404;
    return (
      <Screen edges={['right', 'bottom', 'left']} style={styles.unavailableScreen}>
        <View style={styles.unavailableContent}>
          <RetryPanel
            description={unavailable
              ? 'This conversation is no longer available or belongs to another workspace.'
              : 'Conversation details could not be loaded.'}
            onRetry={() => void query.refetch()}
            title="Conversation unavailable"
          />
          <Button onPress={backFromUnavailable} variant="secondary">
            {first(params.returnTicketId) ? 'Back to Ticket' : 'Back to Conversations'}
          </Button>
        </View>
      </Screen>
    );
  }

  return (
    <Screen edges={['right', 'bottom', 'left']} style={styles.screen}>
      <FlatList
        contentContainerStyle={styles.listContent}
        data={messages}
        initialNumToRender={12}
        keyExtractor={(item) => item.id}
        testID="conversation-transcript"
        ListHeaderComponent={(
          <View style={styles.header}>
            <View style={styles.heading}>
              <AppText accessibilityRole="header" variant="title">
                {customerIdentity(conversation.customer)}
              </AppText>
              <View style={styles.badges}>
                <Badge tone={conversation.status === 'OPEN' ? 'success' : 'neutral'}>
                  {conversation.status.toLowerCase()}
                </Badge>
                <Badge tone="primary">
                  {conversation.channel === 'CUSTOM_API' ? 'Custom API' : 'Widget'}
                </Badge>
              </View>
            </View>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>Customer</AppText>
              <DetailRow label="Name" value={available(conversation.customer.name)} />
              <DetailRow label="Email" value={available(conversation.customer.email)} />
              <DetailRow label="External ID" value={available(conversation.customer.externalId)} />
              <DetailRow label="Started" value={formatDate(conversation.createdAt)} />
              <DetailRow label="Closed" value={conversation.closedAt ? formatDate(conversation.closedAt) : 'Not closed'} />
            </Card>

            <Card style={styles.card}>
              <AppText style={styles.sectionTitle}>Customer metadata</AppText>
              {metadata.length ? metadata.map((entry) => (
                <DetailRow key={entry.key} label={entry.key} value={entry.value} />
              )) : <AppText muted>No displayable metadata is available.</AppText>}
            </Card>

            {confirmation ? (
              <AppText accessibilityRole="alert" style={{ color: theme.colors.successText }}>
                {confirmation}
              </AppText>
            ) : null}
            {actionError ? (
              <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }}>
                {actionError}
              </AppText>
            ) : null}
            <Button
              disabled={conversation.status === 'CLOSED'}
              onPress={() => setCloseVisible(true)}
              variant="danger">
              {conversation.status === 'CLOSED' ? 'Conversation closed' : 'Close conversation'}
            </Button>
            <Separator />
            <AppText accessibilityRole="header" variant="heading">Transcript</AppText>
          </View>
        )}
        ListEmptyComponent={(
          <EmptyState
            description="No messages are available for this conversation."
            title="No messages"
          />
        )}
        maxToRenderPerBatch={12}
        refreshControl={(
          <RefreshControl
            onRefresh={() => void query.refetch()}
            refreshing={query.isFetching}
            tintColor={theme.colors.primary}
          />
        )}
        removeClippedSubviews
        renderItem={({ item }) => <MessageCard message={item} onCitation={setCitation} />}
        showsVerticalScrollIndicator={false}
        windowSize={7}
      />

      <CitationDetailSheet citation={citation} onDismiss={() => setCitation(null)} />
      <Dialog
        actions={(
          <>
            <Button onPress={() => setCloseVisible(false)} variant="secondary">Cancel</Button>
            <Button loading={closeState.isLoading} onPress={() => void handleClose()} variant="danger">
              Close
            </Button>
          </>
        )}
        description="Closing ends this customer conversation. Its transcript remains available for review."
        onDismiss={() => setCloseVisible(false)}
        title="Close conversation?"
        visible={closeVisible}
      />
    </Screen>
  );
}

function MessageCard({
  message,
  onCitation,
}: {
  message: ConversationMessage;
  onCitation: (citation: ConversationCitation) => void;
}) {
  const theme = useAppTheme();
  const draft = ticketDraftFromAction(message.action);
  const roleLabel = {
    user: 'Customer',
    assistant: 'Assistant',
    system: 'System',
  }[message.role];

  return (
    <Card
      style={[
        styles.messageCard,
        message.role === 'user' ? { borderColor: theme.colors.primary } : null,
        message.role === 'system' ? { backgroundColor: theme.colors.primarySoft } : null,
      ]}>
      <View style={styles.messageHeader}>
        <Badge tone={message.role === 'assistant' ? 'primary' : 'neutral'}>{roleLabel}</Badge>
        {message.sequenceNumber !== null ? (
          <AppText muted variant="caption">#{message.sequenceNumber}</AppText>
        ) : null}
      </View>
      <AppText>{message.content}</AppText>
      {message.citations.length ? (
        <View style={styles.citations}>
          {message.citations.map((item, index) => (
            <Pressable
              accessibilityLabel={`Citation ${index + 1}: ${item.sourceName}`}
              accessibilityRole="button"
              key={`${item.id}-${index}`}
              onPress={() => onCitation(item)}
              style={[styles.citationButton, { borderColor: theme.colors.border }]}>
              <AppText style={{ color: theme.colors.primaryText }} variant="bodySmall">
                [{item.id}] {item.sourceName}
              </AppText>
            </Pressable>
          ))}
        </View>
      ) : null}
      {draft ? (
        <View style={[styles.ticketDraft, { borderColor: theme.colors.warningText }]}>
          <AppText style={styles.sectionTitle}>Ticket draft</AppText>
          <AppText style={styles.draftTitle}>{draft.title}</AppText>
          <AppText>{draft.description}</AppText>
          <AppText muted variant="bodySmall">Draft only—no ticket has been created.</AppText>
        </View>
      ) : null}
    </Card>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailRow}>
      <AppText muted style={styles.detailLabel} variant="bodySmall">{label}</AppText>
      <AppText style={styles.detailValue} variant="bodySmall">{value}</AppText>
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
  badges: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  card: { gap: spacing.md },
  citationButton: { alignSelf: 'flex-start', borderRadius: 999, borderWidth: 1, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  citations: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  detailLabel: { flexBasis: 100, fontWeight: '600' },
  detailRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md },
  detailValue: { flex: 1 },
  draftTitle: { fontWeight: '700' },
  header: { gap: spacing.lg, paddingBottom: spacing.lg },
  heading: { gap: spacing.md },
  listContent: { paddingVertical: spacing.xl },
  messageCard: { gap: spacing.md, marginBottom: spacing.md },
  messageHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  screen: { paddingHorizontal: spacing.xl },
  sectionTitle: { fontWeight: '700' },
  ticketDraft: { borderLeftWidth: 3, gap: spacing.sm, paddingLeft: spacing.md },
  unavailableContent: { flex: 1, gap: spacing.lg, justifyContent: 'center' },
  unavailableScreen: { paddingHorizontal: spacing.xl },
});

import { router, useLocalSearchParams, type Href } from 'expo-router';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  View,
} from 'react-native';

import { EmptyState } from '@/components/feedback/empty-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { Screen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Sheet } from '@/components/ui/sheet';
import { radii, spacing } from '@/constants/theme';
import {
  CONVERSATION_PAGE_SIZE,
  useLazyListConversationsQuery,
  useListConversationsQuery,
} from '@/features/conversations/api/conversations-api';
import {
  conversationFiltersFromRoute,
  conversationFiltersToRoute,
  customerIdentity,
  mergeConversationPages,
} from '@/features/conversations/model/conversation-state';
import {
  CONVERSATION_CHANNELS,
  CONVERSATION_STATUSES,
  type ConversationFilters,
  type ConversationListItem,
} from '@/features/conversations/types';
import { useAppTheme } from '@/hooks/use-app-theme';

export function ConversationsScreen() {
  const theme = useAppTheme();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const filters = useMemo(() => conversationFiltersFromRoute(params), [params]);
  const [sheetVisible, setSheetVisible] = useState(false);
  const [extraPages, setExtraPages] = useState<ConversationListItem[][]>([]);
  const [moreAfterExtraPages, setMoreAfterExtraPages] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState(false);
  const firstPageQuery = useListConversationsQuery({
    ...filters,
    limit: CONVERSATION_PAGE_SIZE,
    offset: 0,
  });
  const [loadPage] = useLazyListConversationsQuery();
  const conversations = useMemo(
    () => mergeConversationPages([firstPageQuery.data ?? [], ...extraPages]),
    [extraPages, firstPageQuery.data],
  );
  const hasMore = extraPages.length === 0
    ? (firstPageQuery.data?.length ?? 0) === CONVERSATION_PAGE_SIZE
    : moreAfterExtraPages;

  function updateFilters(next: Partial<ConversationFilters>) {
    setExtraPages([]);
    setMoreAfterExtraPages(true);
    setLoadMoreError(false);
    router.setParams(conversationFiltersToRoute({ ...filters, ...next }));
  }

  function clearFilters() {
    setExtraPages([]);
    setMoreAfterExtraPages(true);
    setLoadMoreError(false);
    router.setParams(conversationFiltersToRoute({}));
  }

  async function loadNextPage() {
    if (!hasMore || loadingMore || firstPageQuery.isFetching) return;
    setLoadingMore(true);
    setLoadMoreError(false);
    try {
      const page = await loadPage({
        ...filters,
        limit: CONVERSATION_PAGE_SIZE,
        offset: (extraPages.length + 1) * CONVERSATION_PAGE_SIZE,
      }, false).unwrap();
      setExtraPages((current) => [...current, page]);
      setMoreAfterExtraPages(page.length === CONVERSATION_PAGE_SIZE);
    } catch {
      setLoadMoreError(true);
    } finally {
      setLoadingMore(false);
    }
  }

  function openConversation(conversationId: string) {
    router.push({
      pathname: '/conversations/[conversationId]',
      params: { conversationId, ...conversationFiltersToRoute(filters) },
    } as unknown as Href);
  }

  return (
    <Screen edges={['right', 'bottom', 'left']} style={styles.screen}>
      <FlatList
        contentContainerStyle={styles.listContent}
        data={conversations}
        keyExtractor={(item) => item.id}
        testID="conversation-list"
        ListHeaderComponent={(
          <View style={styles.header}>
            <View style={styles.headingRow}>
              <View style={styles.headingCopy}>
                <AppText accessibilityRole="header" variant="title">Conversations</AppText>
                <AppText muted>Review customer chats from the widget and Custom API.</AppText>
              </View>
              <Button onPress={() => setSheetVisible(true)} style={styles.compactButton} variant="secondary">
                Filters
              </Button>
            </View>
            {filters.status || filters.channel ? (
              <View style={styles.filterSummary}>
                {filters.status ? <Badge>{filters.status.toLowerCase()}</Badge> : null}
                {filters.channel ? <Badge tone="primary">{channelLabel(filters.channel)}</Badge> : null}
                <Button onPress={clearFilters} style={styles.compactButton} variant="ghost">
                  Clear
                </Button>
              </View>
            ) : null}
            {firstPageQuery.isError && conversations.length === 0 ? (
              <RetryPanel
                description="Customer conversations could not be loaded."
                onRetry={() => void firstPageQuery.refetch()}
                title="Unable to load conversations"
              />
            ) : null}
          </View>
        )}
        ListEmptyComponent={firstPageQuery.isLoading ? (
          <View accessibilityLabel="Loading conversations" style={styles.centered}>
            <ActivityIndicator color={theme.colors.primary} />
            <AppText muted>Loading conversations…</AppText>
          </View>
        ) : firstPageQuery.isError ? null : (
          <EmptyState
            description="No customer conversations match the selected filters."
            title="No conversations"
          />
        )}
        ListFooterComponent={conversations.length ? (
          <View style={styles.footer}>
            {loadingMore ? (
              <ActivityIndicator accessibilityLabel="Loading more conversations" color={theme.colors.primary} />
            ) : null}
            {loadMoreError && !loadingMore ? (
              <RetryPanel
                description="The next page could not be loaded. Your current results are still available."
                onRetry={() => void loadNextPage()}
                title="Unable to load more"
              />
            ) : null}
            {!loadMoreError && !loadingMore && hasMore ? (
              <Button onPress={() => void loadNextPage()} variant="secondary">Load more</Button>
            ) : null}
            {!hasMore ? <AppText muted variant="caption">All conversations loaded</AppText> : null}
          </View>
        ) : null}
        onEndReached={() => void loadNextPage()}
        onEndReachedThreshold={0.4}
        refreshControl={(
          <RefreshControl
            onRefresh={() => {
              setExtraPages([]);
              setMoreAfterExtraPages(true);
              setLoadMoreError(false);
              void firstPageQuery.refetch();
            }}
            refreshing={firstPageQuery.isFetching && !loadingMore}
            tintColor={theme.colors.primary}
          />
        )}
        renderItem={({ item }) => (
          <Pressable
            accessibilityLabel={`Open conversation with ${customerIdentity(item.customer)}`}
            accessibilityRole="button"
            onPress={() => openConversation(item.id)}>
            <Card style={styles.conversationCard}>
              <View style={styles.cardTopRow}>
                <View style={styles.cardCopy}>
                  <AppText numberOfLines={2} style={styles.identity}>
                    {customerIdentity(item.customer)}
                  </AppText>
                  <AppText muted variant="bodySmall">
                    {item.messageCount} {item.messageCount === 1 ? 'message' : 'messages'} · {formatDate(item.createdAt)}
                  </AppText>
                </View>
                <Badge tone={item.status === 'OPEN' ? 'success' : 'neutral'}>
                  {item.status.toLowerCase()}
                </Badge>
              </View>
              <Badge tone="primary">{channelLabel(item.channel)}</Badge>
            </Card>
          </Pressable>
        )}
        showsVerticalScrollIndicator={false}
      />

      <Sheet onDismiss={() => setSheetVisible(false)} title="Conversation filters" visible={sheetVisible}>
        <FilterGroup
          label="Status"
          onSelect={(status) => updateFilters({ status: status as ConversationFilters['status'] })}
          options={CONVERSATION_STATUSES}
          selected={filters.status}
        />
        <FilterGroup
          label="Channel"
          onSelect={(channel) => updateFilters({ channel: channel as ConversationFilters['channel'] })}
          options={CONVERSATION_CHANNELS}
          selected={filters.channel}
        />
      </Sheet>
    </Screen>
  );
}

function FilterGroup({
  label,
  onSelect,
  options,
  selected,
}: {
  label: string;
  onSelect: (value: string | undefined) => void;
  options: readonly string[];
  selected?: string;
}) {
  return (
    <View style={styles.filterGroup}>
      <AppText style={styles.groupLabel}>{label}</AppText>
      <View style={styles.choiceRow}>
        <FilterChoice label="Any" onPress={() => onSelect(undefined)} selected={!selected} />
        {options.map((option) => (
          <FilterChoice
            key={option}
            label={option === 'CUSTOM_API' ? 'Custom API' : option.toLowerCase()}
            onPress={() => onSelect(option)}
            selected={selected === option}
          />
        ))}
      </View>
    </View>
  );
}

function FilterChoice({ label, onPress, selected }: {
  label: string;
  onPress: () => void;
  selected: boolean;
}) {
  const theme = useAppTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[
        styles.filterChoice,
        {
          backgroundColor: selected ? theme.colors.primarySoft : theme.colors.surface,
          borderColor: selected ? theme.colors.primary : theme.colors.border,
        },
      ]}>
      <AppText style={{ color: selected ? theme.colors.primaryText : theme.colors.text }} variant="bodySmall">
        {label}
      </AppText>
    </Pressable>
  );
}

function channelLabel(channel: ConversationListItem['channel']) {
  return channel === 'CUSTOM_API' ? 'Custom API' : 'Widget';
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleString();
}

const styles = StyleSheet.create({
  cardCopy: { flex: 1, gap: spacing.xs, minWidth: 0 },
  cardTopRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md, justifyContent: 'space-between' },
  centered: { alignItems: 'center', gap: spacing.md, paddingVertical: spacing.xxxl },
  choiceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  compactButton: { alignSelf: 'flex-start' },
  conversationCard: { gap: spacing.md, marginBottom: spacing.md },
  filterChoice: { borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm },
  filterGroup: { gap: spacing.md, paddingBottom: spacing.lg },
  filterSummary: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  footer: { alignItems: 'center', gap: spacing.md, paddingVertical: spacing.xl },
  groupLabel: { fontWeight: '700' },
  header: { gap: spacing.lg, paddingBottom: spacing.lg },
  headingCopy: { flex: 1, gap: spacing.xs },
  headingRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md },
  identity: { fontWeight: '700' },
  listContent: { paddingVertical: spacing.xl },
  screen: { paddingHorizontal: spacing.xl },
});

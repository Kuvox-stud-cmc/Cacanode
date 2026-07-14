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
  TICKET_PAGE_SIZE,
  useLazyListTicketsQuery,
  useListTicketAssigneesQuery,
  useListTicketsQuery,
} from '@/features/tickets/api/tickets-api';
import {
  mergeTicketPages,
  ticketCustomerIdentity,
  ticketFiltersFromRoute,
  ticketFiltersToRoute,
  ticketSourceLabel,
  ticketStatusLabel,
} from '@/features/tickets/model/ticket-state';
import {
  TICKET_PRIORITIES,
  TICKET_SOURCES,
  TICKET_STATUSES,
  type Ticket,
  type TicketFilters,
  type TicketPage,
} from '@/features/tickets/types';
import { useAppTheme } from '@/hooks/use-app-theme';

export function TicketsScreen() {
  const theme = useAppTheme();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const filters = useMemo(() => ticketFiltersFromRoute(params), [params]);
  const [sheetVisible, setSheetVisible] = useState(false);
  const [extraPages, setExtraPages] = useState<TicketPage[]>([]);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState(false);
  const firstPageQuery = useListTicketsQuery({ ...filters, page: 0, size: TICKET_PAGE_SIZE });
  const assigneesQuery = useListTicketAssigneesQuery();
  const [loadPage] = useLazyListTicketsQuery();
  const pages = useMemo(
    () => [firstPageQuery.data, ...extraPages].filter((page): page is TicketPage => Boolean(page)),
    [extraPages, firstPageQuery.data],
  );
  const tickets = useMemo(() => mergeTicketPages(pages), [pages]);
  const lastPage = pages.at(-1);
  const hasMore = Boolean(lastPage && !lastPage.last);

  function updateFilters(next: Partial<TicketFilters>) {
    setExtraPages([]);
    setLoadMoreError(false);
    router.setParams(ticketFiltersToRoute({ ...filters, ...next }));
  }

  function clearFilters() {
    setExtraPages([]);
    setLoadMoreError(false);
    router.setParams(ticketFiltersToRoute({}));
  }

  async function loadNextPage() {
    if (!hasMore || loadingMore || firstPageQuery.isFetching) return;
    setLoadingMore(true);
    setLoadMoreError(false);
    try {
      const page = await loadPage({
        ...filters,
        page: (lastPage?.number ?? 0) + 1,
        size: TICKET_PAGE_SIZE,
      }, false).unwrap();
      setExtraPages((current) => [...current, page]);
    } catch {
      setLoadMoreError(true);
    } finally {
      setLoadingMore(false);
    }
  }

  function openTicket(ticketId: string) {
    router.push({
      pathname: '/tickets/[ticketId]',
      params: { ticketId, ...ticketFiltersToRoute(filters) },
    } as unknown as Href);
  }

  const selectedAssignee = filters.assignee === 'unassigned'
    ? 'Unassigned'
    : assigneesQuery.data?.find((item) => item.id === filters.assignee)?.fullName;

  return (
    <Screen edges={['right', 'bottom', 'left']} style={styles.screen}>
      <FlatList
        contentContainerStyle={styles.listContent}
        data={tickets}
        keyExtractor={(item) => item.id}
        testID="ticket-list"
        ListHeaderComponent={(
          <View style={styles.header}>
            <View style={styles.headingRow}>
              <View style={styles.headingCopy}>
                <AppText accessibilityRole="header" variant="title">Tickets</AppText>
                <AppText muted>Review and update customer support requests.</AppText>
              </View>
              <Button onPress={() => setSheetVisible(true)} style={styles.compactButton} variant="secondary">
                Filters
              </Button>
            </View>
            {filters.status || filters.priority || filters.source || filters.assignee ? (
              <View style={styles.filterSummary}>
                {filters.status ? <Badge>{ticketStatusLabel(filters.status)}</Badge> : null}
                {filters.priority ? <Badge tone="warning">{filters.priority.toLowerCase()}</Badge> : null}
                {filters.source ? <Badge tone="primary">{ticketSourceLabel(filters.source)}</Badge> : null}
                {filters.assignee ? <Badge>{selectedAssignee ?? 'Assigned user'}</Badge> : null}
                <Button onPress={clearFilters} style={styles.compactButton} variant="ghost">Clear</Button>
              </View>
            ) : null}
            {firstPageQuery.isError && tickets.length === 0 ? (
              <RetryPanel
                description="Support tickets could not be loaded."
                onRetry={() => void firstPageQuery.refetch()}
                title="Unable to load tickets"
              />
            ) : null}
          </View>
        )}
        ListEmptyComponent={firstPageQuery.isLoading ? (
          <View accessibilityLabel="Loading tickets" style={styles.centered}>
            <ActivityIndicator color={theme.colors.primary} />
            <AppText muted>Loading tickets…</AppText>
          </View>
        ) : firstPageQuery.isError ? null : (
          <EmptyState description="No tickets match the selected filters." title="No tickets" />
        )}
        ListFooterComponent={tickets.length ? (
          <View style={styles.footer}>
            {loadingMore ? <ActivityIndicator accessibilityLabel="Loading more tickets" color={theme.colors.primary} /> : null}
            {loadMoreError && !loadingMore ? (
              <RetryPanel
                description="The next page could not be loaded. Your current tickets remain available."
                onRetry={() => void loadNextPage()}
                title="Unable to load more"
              />
            ) : null}
            {!loadMoreError && !loadingMore && hasMore ? (
              <Button onPress={() => void loadNextPage()} variant="secondary">Load more</Button>
            ) : null}
            {!hasMore ? <AppText muted variant="caption">All tickets loaded</AppText> : null}
          </View>
        ) : null}
        onEndReached={() => void loadNextPage()}
        onEndReachedThreshold={0.4}
        refreshControl={(
          <RefreshControl
            onRefresh={() => {
              setExtraPages([]);
              setLoadMoreError(false);
              void firstPageQuery.refetch();
              void assigneesQuery.refetch();
            }}
            refreshing={firstPageQuery.isFetching && !loadingMore}
            tintColor={theme.colors.primary}
          />
        )}
        renderItem={({ item }) => (
          <Pressable
            accessibilityLabel={`Open ticket ${item.title}`}
            accessibilityRole="button"
            onPress={() => openTicket(item.id)}>
            <Card style={styles.ticketCard}>
              <View style={styles.cardTopRow}>
                <View style={styles.cardCopy}>
                  <AppText numberOfLines={3} style={styles.title}>{item.title}</AppText>
                  <AppText muted variant="bodySmall">{ticketCustomerIdentity(item)}</AppText>
                  <AppText muted variant="caption">{formatDate(item.createdAt)}</AppText>
                </View>
                <Badge tone={statusTone(item.status)}>{ticketStatusLabel(item.status)}</Badge>
              </View>
              <View style={styles.badges}>
                <Badge tone={priorityTone(item.priority)}>{item.priority.toLowerCase()}</Badge>
                <Badge tone="primary">{ticketSourceLabel(item.source)}</Badge>
                <Badge>{item.assignedToName || 'Unassigned'}</Badge>
              </View>
            </Card>
          </Pressable>
        )}
        showsVerticalScrollIndicator={false}
      />

      <Sheet onDismiss={() => setSheetVisible(false)} title="Ticket filters" visible={sheetVisible}>
        <FilterGroup
          label="Status"
          onSelect={(status) => updateFilters({ status: status as TicketFilters['status'] })}
          options={TICKET_STATUSES.map((value) => ({ label: ticketStatusLabel(value), value }))}
          selected={filters.status}
        />
        <FilterGroup
          label="Priority"
          onSelect={(priority) => updateFilters({ priority: priority as TicketFilters['priority'] })}
          options={TICKET_PRIORITIES.map((value) => ({ label: value.toLowerCase(), value }))}
          selected={filters.priority}
        />
        <FilterGroup
          label="Source"
          onSelect={(source) => updateFilters({ source: source as TicketFilters['source'] })}
          options={TICKET_SOURCES.map((value) => ({ label: ticketSourceLabel(value), value }))}
          selected={filters.source}
        />
        <View style={styles.filterGroup}>
          <AppText style={styles.groupLabel}>Assignee</AppText>
          {assigneesQuery.isError ? (
            <RetryPanel
              description="Assignee choices could not be loaded."
              onRetry={() => void assigneesQuery.refetch()}
              title="Assignees unavailable"
            />
          ) : (
            <View style={styles.choiceRow}>
              <FilterChoice label="Any" onPress={() => updateFilters({ assignee: undefined })} selected={!filters.assignee} />
              <FilterChoice label="Unassigned" onPress={() => updateFilters({ assignee: 'unassigned' })} selected={filters.assignee === 'unassigned'} />
              {(assigneesQuery.data ?? []).map((assignee) => (
                <FilterChoice
                  key={assignee.id}
                  label={assignee.fullName || assignee.email}
                  onPress={() => updateFilters({ assignee: assignee.id })}
                  selected={filters.assignee === assignee.id}
                />
              ))}
            </View>
          )}
        </View>
      </Sheet>
    </Screen>
  );
}

function FilterGroup({ label, onSelect, options, selected }: {
  label: string;
  onSelect: (value: string | undefined) => void;
  options: { label: string; value: string }[];
  selected?: string;
}) {
  return (
    <View style={styles.filterGroup}>
      <AppText style={styles.groupLabel}>{label}</AppText>
      <View style={styles.choiceRow}>
        <FilterChoice label="Any" onPress={() => onSelect(undefined)} selected={!selected} />
        {options.map((option) => (
          <FilterChoice key={option.value} label={option.label} onPress={() => onSelect(option.value)} selected={selected === option.value} />
        ))}
      </View>
    </View>
  );
}

function FilterChoice({ label, onPress, selected }: { label: string; onPress: () => void; selected: boolean }) {
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
      <AppText style={{ color: selected ? theme.colors.primaryText : theme.colors.text }} variant="bodySmall">{label}</AppText>
    </Pressable>
  );
}

function statusTone(status: Ticket['status']): 'neutral' | 'primary' | 'success' {
  if (status === 'OPEN') return 'success';
  if (status === 'IN_PROGRESS') return 'primary';
  return 'neutral';
}

function priorityTone(priority: Ticket['priority']): 'neutral' | 'warning' | 'danger' {
  if (priority === 'URGENT') return 'danger';
  if (priority === 'HIGH') return 'warning';
  return 'neutral';
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleString();
}

const styles = StyleSheet.create({
  badges: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  cardCopy: { flex: 1, gap: spacing.xs, minWidth: 0 },
  cardTopRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md, justifyContent: 'space-between' },
  centered: { alignItems: 'center', gap: spacing.md, paddingVertical: spacing.xxxl },
  choiceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  compactButton: { alignSelf: 'flex-start' },
  filterChoice: { borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm },
  filterGroup: { gap: spacing.md, paddingBottom: spacing.lg },
  filterSummary: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  footer: { alignItems: 'center', gap: spacing.md, paddingVertical: spacing.xl },
  groupLabel: { fontWeight: '700' },
  header: { gap: spacing.lg, paddingBottom: spacing.lg },
  headingCopy: { flex: 1, gap: spacing.xs },
  headingRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md },
  listContent: { paddingVertical: spacing.xl },
  screen: { paddingHorizontal: spacing.xl },
  ticketCard: { gap: spacing.md, marginBottom: spacing.md },
  title: { fontWeight: '700' },
});

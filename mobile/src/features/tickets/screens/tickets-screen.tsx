import { router, useLocalSearchParams, type Href } from 'expo-router';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
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
  ticketFiltersFromRoute,
  ticketFiltersToRoute,
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
  const { i18n, t } = useTranslation();
  const locale = i18n.resolvedLanguage?.startsWith('vi') ? 'vi-VN' : 'en-US';
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
    ? t('tickets.unassigned')
    : assigneesQuery.data?.find((item) => item.id === filters.assignee)?.fullName;
  const statusLabel = (status: Ticket['status']) => t(`tickets.statusLabels.${statusKey(status)}`);
  const priorityLabel = (priority: Ticket['priority']) => t(`tickets.priorityLabels.${priority.toLowerCase() as Lowercase<Ticket['priority']>}`);
  const sourceLabel = (source: Ticket['source']) => t(`tickets.sourceLabels.${source === 'CUSTOM_API' ? 'customApi' : 'widget'}`);

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
                <AppText accessibilityRole="header" variant="title">{t('tickets.title')}</AppText>
                <AppText muted>{t('tickets.description')}</AppText>
              </View>
              <Button onPress={() => setSheetVisible(true)} style={styles.compactButton} variant="secondary">
                {t('tickets.filters')}
              </Button>
            </View>
            {filters.status || filters.priority || filters.source || filters.assignee ? (
              <View style={styles.filterSummary}>
                {filters.status ? <Badge>{statusLabel(filters.status)}</Badge> : null}
                {filters.priority ? <Badge tone="warning">{priorityLabel(filters.priority)}</Badge> : null}
                {filters.source ? <Badge tone="primary">{sourceLabel(filters.source)}</Badge> : null}
                {filters.assignee ? <Badge>{selectedAssignee ?? t('tickets.assignedUser')}</Badge> : null}
                <Button onPress={clearFilters} style={styles.compactButton} variant="ghost">{t('tickets.clear')}</Button>
              </View>
            ) : null}
            {firstPageQuery.isError && tickets.length === 0 ? (
              <RetryPanel
                description={t('tickets.loadError')}
                onRetry={() => void firstPageQuery.refetch()}
                title={t('tickets.unable')}
              />
            ) : null}
          </View>
        )}
        ListEmptyComponent={firstPageQuery.isLoading ? (
          <View accessibilityLabel={t('tickets.loading')} style={styles.centered}>
            <ActivityIndicator color={theme.colors.primary} />
            <AppText muted>{t('tickets.loading')}</AppText>
          </View>
        ) : firstPageQuery.isError ? null : (
          <EmptyState description={t('tickets.emptyDescription')} title={t('tickets.empty')} />
        )}
        ListFooterComponent={tickets.length ? (
          <View style={styles.footer}>
            {loadingMore ? <ActivityIndicator accessibilityLabel={t('tickets.loadingMore')} color={theme.colors.primary} /> : null}
            {loadMoreError && !loadingMore ? (
              <RetryPanel
                description={t('tickets.nextPageError')}
                onRetry={() => void loadNextPage()}
                title={t('tickets.unableMore')}
              />
            ) : null}
            {!loadMoreError && !loadingMore && hasMore ? (
              <Button onPress={() => void loadNextPage()} variant="secondary">{t('tickets.loadMore')}</Button>
            ) : null}
            {!hasMore ? <AppText muted variant="caption">{t('tickets.allLoaded')}</AppText> : null}
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
            accessibilityLabel={t('tickets.openTicket', { title: item.title })}
            accessibilityRole="button"
            onPress={() => openTicket(item.id)}>
            <Card style={styles.ticketCard}>
              <View style={styles.cardTopRow}>
                <View style={styles.cardCopy}>
                  <AppText numberOfLines={3} style={styles.title}>{item.title}</AppText>
                  <AppText muted variant="bodySmall">{customerIdentity(item, t('tickets.customer'))}</AppText>
                  <AppText muted variant="caption">{formatDate(item.createdAt, locale, t('common.dateUnavailable'))}</AppText>
                </View>
                <Badge tone={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
              </View>
              <View style={styles.badges}>
                <Badge tone={priorityTone(item.priority)}>{priorityLabel(item.priority)}</Badge>
                <Badge tone="primary">{sourceLabel(item.source)}</Badge>
                <Badge>{item.assignedToName || t('tickets.unassigned')}</Badge>
              </View>
            </Card>
          </Pressable>
        )}
        showsVerticalScrollIndicator={false}
      />

      <Sheet onDismiss={() => setSheetVisible(false)} title={t('tickets.filterTitle')} visible={sheetVisible}>
        <FilterGroup
          anyLabel={t('tickets.any')}
          label={t('tickets.status')}
          onSelect={(status) => updateFilters({ status: status as TicketFilters['status'] })}
          options={TICKET_STATUSES.map((value) => ({ label: statusLabel(value), value }))}
          selected={filters.status}
        />
        <FilterGroup
          anyLabel={t('tickets.any')}
          label={t('tickets.priority')}
          onSelect={(priority) => updateFilters({ priority: priority as TicketFilters['priority'] })}
          options={TICKET_PRIORITIES.map((value) => ({ label: priorityLabel(value), value }))}
          selected={filters.priority}
        />
        <FilterGroup
          anyLabel={t('tickets.any')}
          label={t('tickets.source')}
          onSelect={(source) => updateFilters({ source: source as TicketFilters['source'] })}
          options={TICKET_SOURCES.map((value) => ({ label: sourceLabel(value), value }))}
          selected={filters.source}
        />
        <View style={styles.filterGroup}>
          <AppText style={styles.groupLabel}>{t('tickets.assignee')}</AppText>
          {assigneesQuery.isError ? (
            <RetryPanel
              description={t('tickets.assigneeChoicesError')}
              onRetry={() => void assigneesQuery.refetch()}
              title={t('tickets.assigneesUnavailable')}
            />
          ) : (
            <View style={styles.choiceRow}>
              <FilterChoice label={t('tickets.any')} onPress={() => updateFilters({ assignee: undefined })} selected={!filters.assignee} />
              <FilterChoice label={t('tickets.unassigned')} onPress={() => updateFilters({ assignee: 'unassigned' })} selected={filters.assignee === 'unassigned'} />
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

function FilterGroup({ anyLabel, label, onSelect, options, selected }: {
  anyLabel: string;
  label: string;
  onSelect: (value: string | undefined) => void;
  options: { label: string; value: string }[];
  selected?: string;
}) {
  return (
    <View style={styles.filterGroup}>
      <AppText style={styles.groupLabel}>{label}</AppText>
      <View style={styles.choiceRow}>
        <FilterChoice label={anyLabel} onPress={() => onSelect(undefined)} selected={!selected} />
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

function statusKey(status: Ticket['status']): 'open' | 'inProgress' | 'resolved' | 'closed' {
  if (status === 'IN_PROGRESS') return 'inProgress';
  return status.toLowerCase() as 'open' | 'resolved' | 'closed';
}

function customerIdentity(ticket: Ticket, fallback: string) {
  return ticket.customerName?.trim()
    || ticket.customerEmail?.trim()
    || ticket.externalUserId?.trim()
    || fallback;
}

function formatDate(value: string, locale: string, unavailable: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? unavailable : date.toLocaleString(locale);
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

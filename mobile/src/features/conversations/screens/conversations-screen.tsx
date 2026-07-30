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
  mergeConversationPages,
} from '@/features/conversations/model/conversation-state';
import {
  CONVERSATION_CHANNELS,
  CONVERSATION_STATUSES,
  type ConversationFilters,
  type ConversationListItem,
} from '@/features/conversations/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useTranslation } from 'react-i18next';

export function ConversationsScreen() {
  const {t,i18n}=useTranslation();const locale=i18n.resolvedLanguage?.startsWith('vi')?'vi-VN':'en-US';
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
                <AppText accessibilityRole="header" variant="title">{t('conversations.title')}</AppText>
                <AppText muted>{t('conversations.description')}</AppText>
              </View>
              <Button onPress={() => setSheetVisible(true)} style={styles.compactButton} variant="secondary">
                {t('conversations.filters')}
              </Button>
            </View>
            {filters.status || filters.channel ? (
              <View style={styles.filterSummary}>
                {filters.status ? <Badge>{t(`tickets.statusLabels.${filters.status==='OPEN'?'open':'closed'}`)}</Badge> : null}
                {filters.channel ? <Badge tone="primary">{channelLabel(filters.channel,t)}</Badge> : null}
                <Button onPress={clearFilters} style={styles.compactButton} variant="ghost">
                  {t('conversations.clearFilters')}
                </Button>
              </View>
            ) : null}
            {firstPageQuery.isError && conversations.length === 0 ? (
              <RetryPanel
                description={t('conversations.loadError')}
                onRetry={() => void firstPageQuery.refetch()}
                title={t('conversations.unable')}
              />
            ) : null}
          </View>
        )}
        ListEmptyComponent={firstPageQuery.isLoading ? (
          <View accessibilityLabel={t('conversations.loading')} style={styles.centered}>
            <ActivityIndicator color={theme.colors.primary} />
            <AppText muted>{t('conversations.loading')}</AppText>
          </View>
        ) : firstPageQuery.isError ? null : (
          <EmptyState
            description={t('conversations.emptyDescription')}
            title={t('conversations.empty')}
          />
        )}
        ListFooterComponent={conversations.length ? (
          <View style={styles.footer}>
            {loadingMore ? (
              <ActivityIndicator accessibilityLabel={t('conversations.loadingMore')} color={theme.colors.primary} />
            ) : null}
            {loadMoreError && !loadingMore ? (
              <RetryPanel
                description={t('conversations.nextPageError')}
                onRetry={() => void loadNextPage()}
                title={t('conversations.unableMore')}
              />
            ) : null}
            {!loadMoreError && !loadingMore && hasMore ? (
              <Button onPress={() => void loadNextPage()} variant="secondary">{t('conversations.loadMore')}</Button>
            ) : null}
            {!hasMore ? <AppText muted variant="caption">{t('conversations.allLoaded')}</AppText> : null}
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
            accessibilityLabel={t('conversations.openHint',{name:localizedCustomerIdentity(item.customer,t('conversations.anonymous'))})}
            accessibilityRole="button"
            onPress={() => openConversation(item.id)}>
            <Card style={styles.conversationCard}>
              <View style={styles.cardTopRow}>
                <View style={styles.cardCopy}>
                  <AppText numberOfLines={2} style={styles.identity}>
                    {localizedCustomerIdentity(item.customer,t('conversations.anonymous'))}
                  </AppText>
                  <AppText muted variant="bodySmall">
                    {t('conversations.messages',{count:item.messageCount})} · {formatDate(item.createdAt,locale,t('conversations.dateUnavailable'))}
                  </AppText>
                </View>
                <Badge tone={item.status === 'OPEN' ? 'success' : 'neutral'}>
                  {t(`tickets.statusLabels.${item.status==='OPEN'?'open':'closed'}`)}
                </Badge>
              </View>
              <Badge tone="primary">{channelLabel(item.channel,t)}</Badge>
            </Card>
          </Pressable>
        )}
        showsVerticalScrollIndicator={false}
      />

      <Sheet onDismiss={() => setSheetVisible(false)} title={t('conversations.filterTitle')} visible={sheetVisible}>
        <FilterGroup
          label={t('conversations.status')}
          onSelect={(status) => updateFilters({ status: status as ConversationFilters['status'] })}
          options={CONVERSATION_STATUSES}
          selected={filters.status}
        />
        <FilterGroup
          label={t('conversations.channel')}
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
  const {t}=useTranslation();
  return (
    <View style={styles.filterGroup}>
      <AppText style={styles.groupLabel}>{label}</AppText>
      <View style={styles.choiceRow}>
        <FilterChoice label={t('common.any')} onPress={() => onSelect(undefined)} selected={!selected} />
        {options.map((option) => (
          <FilterChoice
            key={option}
            label={option==='CUSTOM_API'?t('conversations.customApi'):option==='WIDGET'?t('conversations.widget'):t(`tickets.statusLabels.${option==='OPEN'?'open':'closed'}`)}
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

function channelLabel(channel:ConversationListItem['channel'],t:(key:string)=>string) {
  return channel === 'CUSTOM_API' ? t('conversations.customApi') : t('conversations.widget');
}

function localizedCustomerIdentity(
  customer: ConversationListItem['customer'],
  fallback: string,
) {
  return customer.name?.trim()
    || customer.email?.trim()
    || customer.externalId?.trim()
    || fallback;
}

function formatDate(value:string,locale:string,unavailable:string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? unavailable : date.toLocaleString(locale);
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

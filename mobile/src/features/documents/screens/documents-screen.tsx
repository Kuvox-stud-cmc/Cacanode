import { router, useLocalSearchParams, type Href } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  View,
} from 'react-native';

import { EmptyState } from '@/components/feedback/empty-state';
import { LoadingState } from '@/components/feedback/loading-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { Screen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Sheet } from '@/components/ui/sheet';
import { TextField } from '@/components/ui/text-field';
import { radii, spacing } from '@/constants/theme';
import { useGetTenantWorkspaceQuery } from '@/features/chat/api/workspace-api';
import { useLazyListDocumentsQuery, useListDocumentsQuery } from '@/features/documents/api/documents-api';
import { DocumentBadges } from '@/features/documents/components/document-badges';
import {
  filtersFromRoute,
  filtersToRoute,
  mergeDocumentPages,
  shouldPollFirstDocumentPage,
} from '@/features/documents/model/document-list-state';
import {
  DOCUMENT_PAGE_SIZE,
  formatFileSize,
} from '@/features/documents/model/document-rules';
import {
  DOCUMENT_STATUSES,
  DOCUMENT_TYPES,
  DOCUMENT_VISIBILITIES,
  type DocumentFilters,
  type DocumentListItem,
} from '@/features/documents/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useScreenFocus } from '@/hooks/use-screen-focus';

export function DocumentsScreen() {
  const theme = useAppTheme();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const filters = useMemo(() => filtersFromRoute(params), [params]);
  const [search, setSearch] = useState(filters.q ?? '');
  const [sheetVisible, setSheetVisible] = useState(false);
  const [extraPages, setExtraPages] = useState<DocumentListItem[][]>([]);
  const [moreAfterExtraPages, setMoreAfterExtraPages] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [appState, setAppState] = useState(AppState.currentState);
  const isFocused = useScreenFocus();
  const workspaceQuery = useGetTenantWorkspaceQuery();
  const knowledgeBaseId = workspaceQuery.data?.knowledgeBase.id ?? '';
  const firstPageQuery = useListDocumentsQuery(
    { knowledgeBaseId, page: 0, size: DOCUMENT_PAGE_SIZE, ...filters },
    {
      skip: !knowledgeBaseId,
      pollingInterval: 0,
    },
  );
  const [loadPage] = useLazyListDocumentsQuery();
  const documents = useMemo(
    () => mergeDocumentPages([firstPageQuery.data ?? [], ...extraPages]),
    [extraPages, firstPageQuery.data],
  );
  const hasMore = extraPages.length === 0
    ? (firstPageQuery.data?.length ?? 0) === DOCUMENT_PAGE_SIZE
    : moreAfterExtraPages;
  const shouldPoll = shouldPollFirstDocumentPage({
    appActive: appState === 'active',
    documents: firstPageQuery.data ?? [],
    extraPageCount: extraPages.length,
    focused: isFocused,
  });
  const refetchFirstPage = firstPageQuery.refetch;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', setAppState);
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    if (!shouldPoll) return;
    const interval = setInterval(() => void refetchFirstPage(), 5_000);
    return () => clearInterval(interval);
  }, [refetchFirstPage, shouldPoll]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      const query = search.trim().slice(0, 200);
      if (query !== (filters.q ?? '')) {
        setExtraPages([]);
        setMoreAfterExtraPages(true);
        router.setParams({ q: query || undefined });
      }
    }, 300);
    return () => clearTimeout(timeout);
  }, [filters.q, search]);

  async function loadNextPage() {
    if (!knowledgeBaseId || !hasMore || loadingMore || firstPageQuery.isFetching) return;
    setLoadingMore(true);
    try {
      const page = await loadPage({
        knowledgeBaseId,
        page: extraPages.length + 1,
        size: DOCUMENT_PAGE_SIZE,
        ...filters,
      }, false).unwrap();
      setExtraPages((current) => [...current, page]);
      setMoreAfterExtraPages(page.length === DOCUMENT_PAGE_SIZE);
    } catch {
      // The existing list remains usable; the footer offers an explicit retry.
    } finally {
      setLoadingMore(false);
    }
  }

  function updateFilters(next: Partial<DocumentFilters>) {
    setExtraPages([]);
    setMoreAfterExtraPages(true);
    router.setParams(filtersToRoute({ ...filters, ...next }));
  }

  function clearFilters() {
    setExtraPages([]);
    setMoreAfterExtraPages(true);
    router.setParams(filtersToRoute({ q: filters.q }));
  }

  function openDocument(documentId: string) {
    router.push({
      pathname: '/documents/[documentId]',
      params: { documentId, ...filtersToRoute(filters) },
    } as unknown as Href);
  }

  if (workspaceQuery.isLoading) return <LoadingState description="Loading your knowledge base." />;
  if (workspaceQuery.isError || !knowledgeBaseId) {
    return (
      <Screen edges={['right', 'bottom', 'left']}>
        <View style={styles.centered}>
          <RetryPanel description="Your document workspace could not be loaded." onRetry={() => void workspaceQuery.refetch()} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen edges={['right', 'bottom', 'left']} style={styles.screen}>
      <FlatList
        contentContainerStyle={styles.listContent}
        data={documents}
        keyExtractor={(item) => item.id}
        keyboardShouldPersistTaps="handled"
        ListHeaderComponent={(
          <View style={styles.header}>
            <View style={styles.headingRow}>
              <View style={styles.headingCopy}>
                <AppText accessibilityRole="header" variant="title">Documents</AppText>
                <AppText muted>Browse and manage knowledge-base originals.</AppText>
              </View>
              <Button
                onPress={() => router.push({ pathname: '/documents/upload', params: filtersToRoute(filters) } as unknown as Href)}
                style={styles.compactButton}>
                Upload
              </Button>
            </View>
            <TextField
              label="Search by name"
              maxLength={200}
              onChangeText={setSearch}
              placeholder="Policy, handbook, FAQ…"
              returnKeyType="search"
              value={search}
            />
            <View style={styles.filterRow}>
              <Button onPress={() => setSheetVisible(true)} style={styles.compactButton} variant="secondary">
                Filters
              </Button>
              {filters.status || filters.type || filters.visibility ? (
                <Button
                  onPress={clearFilters}
                  style={styles.compactButton}
                  variant="ghost">
                  Clear filters
                </Button>
              ) : null}
            </View>
            {firstPageQuery.isError && documents.length === 0 ? (
              <RetryPanel onRetry={() => void firstPageQuery.refetch()} title="Unable to load documents" />
            ) : null}
          </View>
        )}
        ListEmptyComponent={firstPageQuery.isLoading ? (
          <LoadingState description="Loading documents." />
        ) : firstPageQuery.isError ? null : (
          <EmptyState
            actionLabel="Upload document"
            description="No documents match the current filters."
            onAction={() => router.push('/documents/upload' as Href)}
            title="No documents"
          />
        )}
        ListFooterComponent={documents.length ? (
          <View style={styles.footer}>
            {loadingMore ? <ActivityIndicator accessibilityLabel="Loading more documents" color={theme.colors.primary} /> : null}
            {!loadingMore && hasMore ? (
              <Button onPress={() => void loadNextPage()} variant="secondary">Load more</Button>
            ) : null}
            {!hasMore ? <AppText muted variant="caption">All documents loaded</AppText> : null}
          </View>
        ) : null}
        onEndReached={() => void loadNextPage()}
        onEndReachedThreshold={0.4}
        refreshControl={(
          <RefreshControl
            refreshing={firstPageQuery.isFetching && !loadingMore}
            onRefresh={() => {
              setExtraPages([]);
              setMoreAfterExtraPages(true);
              void firstPageQuery.refetch();
            }}
            tintColor={theme.colors.primary}
          />
        )}
        renderItem={({ item }) => (
          <Pressable accessibilityRole="button" onPress={() => openDocument(item.id)}>
            <Card style={styles.documentCard}>
              <View style={styles.documentCopy}>
                <AppText numberOfLines={2} style={styles.fileName}>{item.fileName}</AppText>
                <AppText muted variant="bodySmall">
                  {item.fileType} · {formatFileSize(item.fileSizeBytes)} · {new Date(item.uploadedAt).toLocaleDateString()}
                </AppText>
              </View>
              <DocumentBadges status={item.status} visibility={item.visibility} />
            </Card>
          </Pressable>
        )}
        showsVerticalScrollIndicator={false}
      />
      <Sheet onDismiss={() => setSheetVisible(false)} title="Document filters" visible={sheetVisible}>
        <FilterGroup
          label="Status"
          onSelect={(status) => updateFilters({ status: status as DocumentFilters['status'] })}
          options={DOCUMENT_STATUSES}
          selected={filters.status}
        />
        <FilterGroup
          label="File type"
          onSelect={(type) => updateFilters({ type: type as DocumentFilters['type'] })}
          options={DOCUMENT_TYPES}
          selected={filters.type}
        />
        <FilterGroup
          label="Visibility"
          onSelect={(visibility) => updateFilters({ visibility: visibility as DocumentFilters['visibility'] })}
          options={DOCUMENT_VISIBILITIES}
          selected={filters.visibility}
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
  const theme = useAppTheme();
  return (
    <View style={styles.filterGroup}>
      <AppText style={styles.groupLabel}>{label}</AppText>
      <View style={styles.choiceRow}>
        <FilterChoice label="Any" onPress={() => onSelect(undefined)} selected={!selected} />
        {options.map((option) => (
          <FilterChoice
            key={option}
            label={option.replaceAll('_', ' ').toLowerCase()}
            onPress={() => onSelect(option)}
            selected={selected === option}
          />
        ))}
      </View>
      <View style={{ backgroundColor: theme.colors.border, height: StyleSheet.hairlineWidth }} />
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
        styles.choice,
        { backgroundColor: selected ? theme.colors.primarySoft : theme.colors.surface, borderColor: selected ? theme.colors.primary : theme.colors.border },
      ]}>
      <AppText style={selected ? { color: theme.colors.primaryText, fontWeight: '700' } : undefined} variant="bodySmall">
        {label}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  centered: { flex: 1, justifyContent: 'center' },
  choice: { borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  choiceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  compactButton: { minHeight: 42, paddingVertical: spacing.sm },
  documentCard: { gap: spacing.md, marginBottom: spacing.md, padding: spacing.lg },
  documentCopy: { gap: spacing.xs },
  fileName: { fontWeight: '700' },
  filterGroup: { gap: spacing.md, marginBottom: spacing.lg },
  filterRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  footer: { alignItems: 'center', gap: spacing.md, paddingVertical: spacing.xl },
  groupLabel: { fontWeight: '700' },
  header: { gap: spacing.lg, paddingBottom: spacing.xl },
  headingCopy: { flex: 1, gap: spacing.xs, minWidth: 0 },
  headingRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md },
  listContent: { flexGrow: 1, paddingVertical: spacing.xl },
  screen: { paddingHorizontal: spacing.xl },
});

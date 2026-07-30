import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { AppState, RefreshControl, StyleSheet, View } from 'react-native';

import { LoadingState } from '@/components/feedback/loading-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { ScrollScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Sheet } from '@/components/ui/sheet';
import { spacing } from '@/constants/theme';
import {
  useDeleteDocumentMutation,
  useDownloadDocumentMutation,
  useGetDocumentQuery,
  useUpdateDocumentVisibilityMutation,
} from '@/features/documents/api/documents-api';
import { DocumentBadges } from '@/features/documents/components/document-badges';
import { filtersFromRoute, filtersToRoute } from '@/features/documents/model/document-list-state';
import {
  formatFileSize,
  isProcessingStatus,
  safeProcessingFailure,
} from '@/features/documents/model/document-rules';
import { shareTemporaryDownload } from '@/features/documents/services/document-files';
import type { DocumentVisibility } from '@/features/documents/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useScreenFocus } from '@/hooks/use-screen-focus';
import type { ApiError } from '@/services/api/errors';
import { useAppSelector } from '@/store/hooks';
import { useTranslation } from 'react-i18next';

export function DocumentDetailScreen() {
  const {t,i18n}=useTranslation();const isVietnamese=i18n.resolvedLanguage?.startsWith('vi')??false;const locale=isVietnamese?'vi-VN':'en-US';
  const theme = useAppTheme();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const documentId = first(params.documentId) ?? '';
  const filters = useMemo(() => filtersFromRoute(params), [params]);
  const role = useAppSelector((state) => state.auth.user?.role);
  const isAdmin = role === 'TENANT_ADMIN';
  const isFocused = useScreenFocus();
  const [appState, setAppState] = useState(AppState.currentState);
  const [deleteVisible, setDeleteVisible] = useState(false);
  const [visibilityVisible, setVisibilityVisible] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const query = useGetDocumentQuery(documentId, {
    skip: !documentId,
  });
  const [download, downloadState] = useDownloadDocumentMutation();
  const [updateVisibility, visibilityState] = useUpdateDocumentVisibilityMutation();
  const [deleteDocument, deleteState] = useDeleteDocumentMutation();
  const document = query.data;
  const refetchDocument = query.refetch;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', setAppState);
    return () => subscription.remove();
  }, []);

  const shouldPoll = Boolean(document && isProcessingStatus(document.status));
  useEffect(() => {
    if (!shouldPoll || !isFocused || appState !== 'active') return;
    const interval = setInterval(() => void refetchDocument(), 3_000);
    return () => clearInterval(interval);
  }, [appState, isFocused, refetchDocument, shouldPoll]);

  async function handleDownload() {
    if (!document) return;
    setActionError(null);
    try {
      const temporary = await download({
        documentId: document.id,
        fallbackFileName: document.fileName,
      }).unwrap();
      await shareTemporaryDownload(temporary);
    } catch (error) {
      setActionError(apiMessage(error,t('documents.downloadError'),isVietnamese));
    }
  }

  async function handleVisibility(visibility: DocumentVisibility) {
    if (!document || !isAdmin) return;
    setActionError(null);
    try {
      await updateVisibility({ documentId: document.id, visibility }).unwrap();
      setVisibilityVisible(false);
    } catch (error) {
      setActionError(apiMessage(error,t('documents.visibilityError'),isVietnamese));
    }
  }

  async function handleDelete() {
    if (!document || !isAdmin || isProcessingStatus(document.status)) return;
    setActionError(null);
    try {
      await deleteDocument(document.id).unwrap();
      setDeleteVisible(false);
      router.replace({ pathname: '/documents', params: filtersToRoute(filters) });
    } catch (error) {
      setActionError(apiMessage(error,t('documents.deleteError'),isVietnamese));
    }
  }

  if (query.isLoading) return <LoadingState description={t('documents.metadataLoading')} />;
  if (query.isError || !document) {
    const unavailable = (query.error as ApiError | undefined)?.status === 404;
    return (
      <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.screen}>
        <RetryPanel
          description={unavailable
            ? t('documents.gone')
            : t('documents.detailsUnavailable')}
          onRetry={() => void query.refetch()}
          title={t('documents.detailUnavailable')}
        />
        <Button onPress={() => router.replace({ pathname: '/documents', params: filtersToRoute(filters) })} variant="secondary">
          {t('documents.back')}
        </Button>
      </ScrollScreen>
    );
  }

  return (
    <ScrollScreen
      edges={['right', 'bottom', 'left']}
      contentContainerStyle={styles.screen}
      refreshControl={(
        <RefreshControl
          onRefresh={() => void query.refetch()}
          refreshing={query.isFetching}
          tintColor={theme.colors.primary}
        />
      )}>
      <View style={styles.heading}>
        <AppText accessibilityRole="header" variant="title">{document.fileName}</AppText>
        <DocumentBadges status={document.status} visibility={document.visibility} />
      </View>

      <Card style={styles.card}>
        <MetadataRow label={t('documents.type')} value={document.fileType} />
        <MetadataRow label={t('documents.size')} value={formatFileSize(document.fileSizeBytes)} />
        <MetadataRow label={t('documents.uploaded')} value={new Date(document.uploadedAt).toLocaleString(locale)} />
        <MetadataRow label={t('documents.chunks')} value={document.chunkCount?.toString() ?? t('documents.chunksPending')} />
      </Card>

      {isProcessingStatus(document.status) ? (
        <Card style={styles.card}>
          <AppText style={styles.strong}>{t('documents.processing')}</AppText>
          <AppText muted>{t('documents.processingDescription')}</AppText>
        </Card>
      ) : null}
      {document.status === 'FAILED' ? (
        <Card style={styles.card}>
          <AppText style={[styles.strong, { color: theme.colors.dangerText }]}>{t('documents.failedTitle')}</AppText>
          <AppText>{isVietnamese ? t('documents.processingFailure') : safeProcessingFailure(document.errorMessage)}</AppText>
        </Card>
      ) : null}
      {document.status === 'COMPLETED' ? (
        <Card style={styles.card}>
          <AppText style={[styles.strong, { color: theme.colors.successText }]}>{t('documents.ready')}</AppText>
          <AppText muted>{t('documents.readyDescription')}</AppText>
        </Card>
      ) : null}

      {actionError ? <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }}>{actionError}</AppText> : null}

      <View style={styles.actions}>
        <Button loading={downloadState.isLoading} onPress={() => void handleDownload()}>
          {t('documents.downloadShare')}
        </Button>
        {isAdmin ? (
          <Button onPress={() => setVisibilityVisible(true)} variant="secondary">
            {t('documents.changeVisibility')}
          </Button>
        ) : null}
        {isAdmin ? (
          <Button
            disabled={isProcessingStatus(document.status)}
            onPress={() => setDeleteVisible(true)}
            variant="danger">
            {t('documents.delete')}
          </Button>
        ) : null}
      </View>

      <Sheet onDismiss={() => setVisibilityVisible(false)} title={t('documents.visibilityTitle')} visible={visibilityVisible}>
        <View style={styles.sheetActions}>
          <Button
            disabled={visibilityState.isLoading || document.visibility === 'EMPLOYEE_ONLY'}
            onPress={() => void handleVisibility('EMPLOYEE_ONLY')}
            variant="secondary">
            {t('documents.employeeOnly')}
          </Button>
          <Button
            disabled={visibilityState.isLoading || document.visibility === 'CUSTOMER_AND_EMPLOYEE'}
            onPress={() => void handleVisibility('CUSTOMER_AND_EMPLOYEE')}
            variant="secondary">
            {t('documents.customersAndEmployees')}
          </Button>
        </View>
      </Sheet>

      <Dialog
        actions={(
          <>
            <Button onPress={() => setDeleteVisible(false)} variant="secondary">{t('common.cancel')}</Button>
            <Button loading={deleteState.isLoading} onPress={() => void handleDelete()} variant="danger">
              {t('common.delete')}
            </Button>
          </>
        )}
        description={t('documents.deleteDescription')}
        onDismiss={() => setDeleteVisible(false)}
        title={t('documents.deleteTitle')}
        visible={deleteVisible}
      />
    </ScrollScreen>
  );
}

function MetadataRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metadataRow}>
      <AppText muted variant="bodySmall">{label}</AppText>
      <AppText style={styles.metadataValue}>{value}</AppText>
    </View>
  );
}

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function apiMessage(error: unknown, fallback: string, forceFallback: boolean) {
  return !forceFallback && typeof error === 'object' && error !== null && 'message' in error
    && typeof error.message === 'string' ? error.message : fallback;
}

const styles = StyleSheet.create({
  actions: { gap: spacing.md },
  card: { gap: spacing.md },
  heading: { gap: spacing.md },
  metadataRow: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.lg, justifyContent: 'space-between' },
  metadataValue: { flex: 1, fontWeight: '600', textAlign: 'right' },
  screen: { gap: spacing.xl },
  sheetActions: { gap: spacing.md },
  strong: { fontWeight: '700' },
});

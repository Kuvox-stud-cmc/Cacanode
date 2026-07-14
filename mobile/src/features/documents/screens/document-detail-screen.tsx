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

export function DocumentDetailScreen() {
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
      setActionError(apiMessage(error, 'The document could not be downloaded or shared.'));
    }
  }

  async function handleVisibility(visibility: DocumentVisibility) {
    if (!document || !isAdmin) return;
    setActionError(null);
    try {
      await updateVisibility({ documentId: document.id, visibility }).unwrap();
      setVisibilityVisible(false);
    } catch (error) {
      setActionError(apiMessage(error, 'Visibility could not be updated.'));
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
      setActionError(apiMessage(error, 'The document could not be deleted.'));
    }
  }

  if (query.isLoading) return <LoadingState description="Loading document metadata." />;
  if (query.isError || !document) {
    const unavailable = (query.error as ApiError | undefined)?.status === 404;
    return (
      <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.screen}>
        <RetryPanel
          description={unavailable
            ? 'This document is no longer available or you do not have access.'
            : 'Document details could not be loaded.'}
          onRetry={() => void query.refetch()}
          title="Document unavailable"
        />
        <Button onPress={() => router.replace({ pathname: '/documents', params: filtersToRoute(filters) })} variant="secondary">
          Back to Documents
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
        <MetadataRow label="Type" value={document.fileType} />
        <MetadataRow label="Size" value={formatFileSize(document.fileSizeBytes)} />
        <MetadataRow label="Uploaded" value={new Date(document.uploadedAt).toLocaleString()} />
        <MetadataRow label="Chunks" value={document.chunkCount?.toString() ?? 'Not available yet'} />
      </Card>

      {isProcessingStatus(document.status) ? (
        <Card style={styles.card}>
          <AppText style={styles.strong}>Processing</AppText>
          <AppText muted>The original is stored. This screen refreshes while indexing continues.</AppText>
        </Card>
      ) : null}
      {document.status === 'FAILED' ? (
        <Card style={styles.card}>
          <AppText style={[styles.strong, { color: theme.colors.dangerText }]}>Processing failed</AppText>
          <AppText>{safeProcessingFailure(document.errorMessage)}</AppText>
        </Card>
      ) : null}
      {document.status === 'COMPLETED' ? (
        <Card style={styles.card}>
          <AppText style={[styles.strong, { color: theme.colors.successText }]}>Ready</AppText>
          <AppText muted>The document is available to the knowledge base.</AppText>
        </Card>
      ) : null}

      {actionError ? <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }}>{actionError}</AppText> : null}

      <View style={styles.actions}>
        <Button loading={downloadState.isLoading} onPress={() => void handleDownload()}>
          Download and share
        </Button>
        {isAdmin ? (
          <Button onPress={() => setVisibilityVisible(true)} variant="secondary">
            Change visibility
          </Button>
        ) : null}
        {isAdmin ? (
          <Button
            disabled={isProcessingStatus(document.status)}
            onPress={() => setDeleteVisible(true)}
            variant="danger">
            Delete document
          </Button>
        ) : null}
      </View>

      <Sheet onDismiss={() => setVisibilityVisible(false)} title="Document visibility" visible={visibilityVisible}>
        <View style={styles.sheetActions}>
          <Button
            disabled={visibilityState.isLoading || document.visibility === 'EMPLOYEE_ONLY'}
            onPress={() => void handleVisibility('EMPLOYEE_ONLY')}
            variant="secondary">
            Employees only
          </Button>
          <Button
            disabled={visibilityState.isLoading || document.visibility === 'CUSTOMER_AND_EMPLOYEE'}
            onPress={() => void handleVisibility('CUSTOMER_AND_EMPLOYEE')}
            variant="secondary">
            Customers and employees
          </Button>
        </View>
      </Sheet>

      <Dialog
        actions={(
          <>
            <Button onPress={() => setDeleteVisible(false)} variant="secondary">Cancel</Button>
            <Button loading={deleteState.isLoading} onPress={() => void handleDelete()} variant="danger">
              Delete
            </Button>
          </>
        )}
        description="This removes the stored original and indexed content. This action cannot be undone."
        onDismiss={() => setDeleteVisible(false)}
        title="Delete document?"
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

function apiMessage(error: unknown, fallback: string) {
  return typeof error === 'object' && error !== null && 'message' in error
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

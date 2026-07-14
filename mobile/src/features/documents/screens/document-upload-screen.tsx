import * as DocumentPicker from 'expo-document-picker';
import { router, useLocalSearchParams, type Href } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { LoadingState } from '@/components/feedback/loading-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { ScrollScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { radii, spacing } from '@/constants/theme';
import { useGetTenantWorkspaceQuery } from '@/features/chat/api/workspace-api';
import { useUploadDocumentMutation } from '@/features/documents/api/documents-api';
import {
  filtersFromRoute,
  filtersToRoute,
  runWithConcurrency,
} from '@/features/documents/model/document-list-state';
import {
  formatFileSize,
  queuePickerAssets,
  safeUploadError,
} from '@/features/documents/model/document-rules';
import { deletePickerCopy } from '@/features/documents/services/document-files';
import type {
  DocumentUploadQueueItem,
  DocumentVisibility,
} from '@/features/documents/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import type { ApiError } from '@/services/api/errors';
import { useAppSelector } from '@/store/hooks';

const PICKER_MIME_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
  'text/markdown',
  'text/x-markdown',
  'text/html',
  'application/xhtml+xml',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'text/csv',
  'application/csv',
  'application/vnd.ms-excel',
  'application/zip',
  'application/octet-stream',
];

export function DocumentUploadScreen() {
  const theme = useAppTheme();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const filters = useMemo(() => filtersFromRoute(params), [params]);
  const role = useAppSelector((state) => state.auth.user?.role);
  const isAdmin = role === 'TENANT_ADMIN';
  const [visibility, setVisibility] = useState<DocumentVisibility>('EMPLOYEE_ONLY');
  const [queue, setQueue] = useState<DocumentUploadQueueItem[]>([]);
  const queueRef = useRef(queue);
  const [running, setRunning] = useState(false);
  const [pickerError, setPickerError] = useState<string | null>(null);
  const workspaceQuery = useGetTenantWorkspaceQuery();
  const [uploadDocument] = useUploadDocumentMutation();
  const knowledgeBaseId = workspaceQuery.data?.knowledgeBase.id;
  const hasAttempted = queue.some((item) => ['succeeded', 'failed', 'ambiguous'].includes(item.status));

  useEffect(() => {
    queueRef.current = queue;
  }, [queue]);

  useEffect(() => () => {
    queueRef.current.forEach((item) => deletePickerCopy(item.uri));
  }, []);

  async function pickFiles() {
    setPickerError(null);
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: PICKER_MIME_TYPES,
        copyToCacheDirectory: true,
        multiple: true,
      });
      if (result.canceled) return;
      setQueue((current) => [...current, ...queuePickerAssets(result.assets, current)]);
    } catch {
      setPickerError('The system file picker could not be opened.');
    }
  }

  function updateItem(localId: string, update: Partial<DocumentUploadQueueItem>) {
    setQueue((current) => current.map((item) => item.localId === localId ? { ...item, ...update } : item));
  }

  function removeItem(item: DocumentUploadQueueItem) {
    deletePickerCopy(item.uri);
    setQueue((current) => current.filter((candidate) => candidate.localId !== item.localId));
  }

  async function uploadBatch() {
    if (!knowledgeBaseId || running) return;
    const ready = queue.filter((item) => item.status === 'ready');
    if (!ready.length) return;
    setRunning(true);
    await runWithConcurrency(ready, 2, async (item) => {
      updateItem(item.localId, { status: 'uploading', errorMessage: null });
      try {
        const response = await uploadDocument({
          knowledgeBaseId,
          visibility,
          file: item,
        }).unwrap();
        deletePickerCopy(item.uri);
        updateItem(item.localId, { status: 'succeeded', response });
      } catch (error) {
        const safe = safeUploadError(error as ApiError);
        updateItem(item.localId, {
          status: safe.ambiguous ? 'ambiguous' : 'failed',
          errorMessage: safe.message,
        });
      }
    });
    setRunning(false);
  }

  if (workspaceQuery.isLoading) return <LoadingState description="Loading your knowledge base." />;
  if (workspaceQuery.isError || !knowledgeBaseId) {
    return (
      <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.screen}>
        <RetryPanel description="Your upload workspace could not be loaded." onRetry={() => void workspaceQuery.refetch()} />
      </ScrollScreen>
    );
  }

  return (
    <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.screen}>
      <View style={styles.heading}>
        <AppText accessibilityRole="header" variant="title">Upload documents</AppText>
        <AppText muted>Select up to 10 supported files. Two files upload at a time.</AppText>
      </View>

      <Card style={styles.card}>
        <AppText style={styles.strong}>Batch visibility</AppText>
        <View style={styles.choiceRow}>
          <VisibilityChoice
            label="Employees only"
            onPress={() => setVisibility('EMPLOYEE_ONLY')}
            selected={visibility === 'EMPLOYEE_ONLY'}
          />
          {isAdmin ? (
            <VisibilityChoice
              label="Customers and employees"
              onPress={() => setVisibility('CUSTOMER_AND_EMPLOYEE')}
              selected={visibility === 'CUSTOMER_AND_EMPLOYEE'}
            />
          ) : null}
        </View>
        {!isAdmin ? <AppText muted variant="caption">Only tenant admins can expose documents to customers.</AppText> : null}
      </Card>

      <Button disabled={running} onPress={() => void pickFiles()} variant="secondary">Select files</Button>
      {pickerError ? <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }}>{pickerError}</AppText> : null}

      <View style={styles.queue}>
        {queue.map((item) => (
          <Card key={item.localId} style={styles.queueItem}>
            <View style={styles.itemHeading}>
              <View style={styles.itemCopy}>
                <AppText numberOfLines={2} style={styles.strong}>{item.name}</AppText>
                <AppText muted variant="bodySmall">{formatFileSize(item.size)} · {item.mimeType || 'Unknown MIME type'}</AppText>
              </View>
              <QueueBadge status={item.status} />
            </View>
            {item.status === 'uploading' ? (
              <View style={styles.progressRow}>
                <ActivityIndicator color={theme.colors.primary} />
                <AppText muted variant="bodySmall">Uploading…</AppText>
              </View>
            ) : null}
            {item.errorMessage ? (
              <AppText accessibilityRole="alert" style={{ color: theme.colors.dangerText }} variant="bodySmall">
                {item.errorMessage}
              </AppText>
            ) : null}
            <View style={styles.itemActions}>
              {item.status === 'failed' ? (
                <Button
                  onPress={() => updateItem(item.localId, { status: 'ready', errorMessage: null })}
                  style={styles.smallButton}
                  variant="secondary">
                  Retry
                </Button>
              ) : null}
              {item.status === 'succeeded' && item.response ? (
                <Button
                  onPress={() => router.push({
                    pathname: '/documents/[documentId]',
                    params: { documentId: item.response!.id, ...filtersToRoute(filters) },
                  } as unknown as Href)}
                  style={styles.smallButton}
                  variant="secondary">
                  View document
                </Button>
              ) : null}
              {!running && item.status !== 'uploading' && item.status !== 'succeeded' ? (
                <Button onPress={() => removeItem(item)} style={styles.smallButton} variant="ghost">Remove</Button>
              ) : null}
            </View>
          </Card>
        ))}
      </View>

      {queue.length === 0 ? (
        <Card style={styles.card}>
          <AppText muted>No files selected yet.</AppText>
        </Card>
      ) : null}

      <Button
        disabled={running || !queue.some((item) => item.status === 'ready')}
        loading={running}
        onPress={() => void uploadBatch()}>
        Upload ready files
      </Button>

      {hasAttempted && !running ? (
        <Button
          onPress={() => router.replace({ pathname: '/documents', params: filtersToRoute(filters) })}
          variant="secondary">
          Finish and return to Documents
        </Button>
      ) : null}
    </ScrollScreen>
  );
}

function VisibilityChoice({ label, onPress, selected }: { label: string; onPress: () => void; selected: boolean }) {
  const theme = useAppTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[
        styles.visibilityChoice,
        { backgroundColor: selected ? theme.colors.primarySoft : theme.colors.surface, borderColor: selected ? theme.colors.primary : theme.colors.border },
      ]}>
      <AppText style={selected ? { color: theme.colors.primaryText, fontWeight: '700' } : undefined} variant="bodySmall">
        {label}
      </AppText>
    </Pressable>
  );
}

function QueueBadge({ status }: { status: DocumentUploadQueueItem['status'] }) {
  const tone = status === 'succeeded'
    ? 'success'
    : status === 'failed' || status === 'rejected' || status === 'ambiguous'
      ? 'danger'
      : status === 'uploading'
        ? 'primary'
        : 'neutral';
  return <Badge tone={tone}>{status.charAt(0).toUpperCase() + status.slice(1)}</Badge>;
}

const styles = StyleSheet.create({
  card: { gap: spacing.md },
  choiceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  heading: { gap: spacing.sm },
  itemActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  itemCopy: { flex: 1, gap: spacing.xs, minWidth: 0 },
  itemHeading: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.md },
  progressRow: { alignItems: 'center', flexDirection: 'row', gap: spacing.sm },
  queue: { gap: spacing.md },
  queueItem: { gap: spacing.md, padding: spacing.lg },
  screen: { gap: spacing.xl },
  smallButton: { minHeight: 40, paddingVertical: spacing.sm },
  strong: { fontWeight: '700' },
  visibilityChoice: { borderRadius: radii.md, borderWidth: 1, padding: spacing.md },
});

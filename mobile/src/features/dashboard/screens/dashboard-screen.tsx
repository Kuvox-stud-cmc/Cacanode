import { Fragment, useCallback } from 'react';
import { useRouter, type Href } from 'expo-router';
import { RefreshControl, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/feedback/empty-state';
import { ErrorState } from '@/components/feedback/error-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { ScrollScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { ListRow } from '@/components/ui/list-row';
import { Separator } from '@/components/ui/separator';
import { spacing } from '@/constants/theme';
import { useGetDashboardSummaryQuery } from '@/features/dashboard/api/dashboard-api';
import { DashboardMetricCard } from '@/features/dashboard/components/dashboard-metric-card';
import { DashboardSkeleton } from '@/features/dashboard/components/dashboard-skeleton';
import {
  buildDashboardMetrics,
  dashboardShortcutsForRole,
  documentStatusPresentation,
  formatBytes,
  formatDashboardDate,
  storagePercentage,
} from '@/features/dashboard/model/dashboard-view-model';
import type { RecentDocument } from '@/features/dashboard/types';
import { useAppTheme } from '@/hooks/use-app-theme';
import type { ApiError } from '@/services/api/errors';
import { useAppSelector } from '@/store/hooks';

export function DashboardScreen() {
  const router = useRouter();
  const theme = useAppTheme();
  const user = useAppSelector((state) => state.auth.user);
  const { data, error, isFetching, isLoading, refetch } = useGetDashboardSummaryQuery();
  const refresh = useCallback(() => {
    void refetch();
  }, [refetch]);
  const errorMessage = dashboardErrorMessage(error as ApiError | undefined);

  if (isLoading && !data) {
    return (
      <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.content}>
        <DashboardSkeleton />
      </ScrollScreen>
    );
  }

  if (!data) {
    return (
      <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.errorContent}>
        <ErrorState
          description={errorMessage}
          onRetry={refresh}
          title="Unable to load your dashboard"
        />
      </ScrollScreen>
    );
  }

  const metrics = buildDashboardMetrics(data);
  const shortcuts = dashboardShortcutsForRole(user?.role);
  const storageProgress = storagePercentage(data.storedDocumentBytes, data.storageLimitBytes);

  return (
    <ScrollScreen
      edges={['right', 'bottom', 'left']}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl
          colors={[theme.colors.primary]}
          onRefresh={refresh}
          refreshing={isFetching && !isLoading}
          testID="dashboard-refresh"
          tintColor={theme.colors.primary}
        />
      }>
      <View style={styles.heading}>
        <View style={styles.headingRow}>
          <View style={styles.headingCopy}>
            <AppText accessibilityRole="header" variant="title">
              Welcome, {firstName(user?.fullName) ?? 'there'}
            </AppText>
            <AppText muted>Your tenant overview and recent activity.</AppText>
          </View>
          <Badge tone="primary">{user?.plan ?? 'Current plan'}</Badge>
        </View>
      </View>

      {error ? (
        <RetryPanel
          description={errorMessage}
          onRetry={refresh}
          title="Dashboard may be out of date"
        />
      ) : null}

      <Card elevated style={styles.section}>
        <AppText accessibilityRole="header" variant="heading">Quick actions</AppText>
        <View>
          {shortcuts.map((shortcut, index) => (
            <Fragment key={shortcut.title}>
              {index > 0 ? <Separator /> : null}
              <ListRow
                accessibilityHint={`Opens ${shortcut.title}`}
                onPress={() => router.push(shortcut.href)}
                subtitle={shortcut.description}
                title={shortcut.title}
                trailing={<Badge>Open</Badge>}
              />
            </Fragment>
          ))}
        </View>
      </Card>

      <View style={styles.sectionHeading}>
        <AppText accessibilityRole="header" variant="heading">Overview</AppText>
        <AppText muted variant="bodySmall">Usage from your current tenant.</AppText>
      </View>
      <View style={styles.metrics}>
        {metrics.map((metric) => (
          <DashboardMetricCard
            key={metric.key}
            metric={metric}
            progress={metric.key === 'storage' ? storageProgress : undefined}
          />
        ))}
      </View>

      <Card elevated style={styles.section}>
        <View style={styles.sectionHeading}>
          <AppText accessibilityRole="header" variant="heading">Recent documents</AppText>
          <AppText muted variant="bodySmall">The five latest uploads for this tenant.</AppText>
        </View>
        {data.recentDocuments.length === 0 ? (
          <EmptyState
            actionLabel="Upload first document"
            description="Your latest uploads will appear here."
            onAction={() => router.push('/documents/upload' as Href)}
            title="No documents uploaded yet"
          />
        ) : (
          <View>
            {data.recentDocuments.map((document, index) => (
              <Fragment key={document.id}>
                {index > 0 ? <Separator /> : null}
                <RecentDocumentRow
                  document={document}
                  onPress={() =>
                    router.push({
                      pathname: '/documents/[documentId]',
                      params: { documentId: document.id },
                    } as unknown as Href)
                  }
                />
              </Fragment>
            ))}
          </View>
        )}
      </Card>
    </ScrollScreen>
  );
}

function RecentDocumentRow({ document, onPress }: { document: RecentDocument; onPress: () => void }) {
  const status = documentStatusPresentation(document.status);
  return (
    <ListRow
      accessibilityHint="Opens document details"
      onPress={onPress}
      subtitle={`${document.fileType} · ${formatBytes(document.fileSizeBytes)} · ${formatDashboardDate(document.uploadedAt)}`}
      title={document.fileName}
      trailing={<Badge tone={status.tone}>{status.label}</Badge>}
    />
  );
}

function firstName(fullName?: string): string | null {
  return fullName?.trim().split(/\s+/)[0] || null;
}

function dashboardErrorMessage(error?: ApiError): string {
  if (error?.kind === 'network' || error?.kind === 'timeout') return error.message;
  return 'Dashboard data is temporarily unavailable. Please try again.';
}

const styles = StyleSheet.create({
  content: { gap: spacing.xl, paddingVertical: spacing.xxl },
  errorContent: { flexGrow: 1, justifyContent: 'center' },
  heading: { gap: spacing.sm },
  headingCopy: { flex: 1, gap: spacing.sm, minWidth: 0 },
  headingRow: { alignItems: 'flex-start', flexDirection: 'row', flexWrap: 'wrap', gap: spacing.lg },
  metrics: { gap: spacing.lg },
  section: { gap: spacing.lg },
  sectionHeading: { gap: spacing.xs },
});

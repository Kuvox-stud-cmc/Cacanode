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
import { useTranslation } from 'react-i18next';

export function DashboardScreen() {
  const {t,i18n}=useTranslation();
  const locale=i18n.resolvedLanguage?.startsWith('vi')?'vi-VN':'en-US';
  const router = useRouter();
  const theme = useAppTheme();
  const user = useAppSelector((state) => state.auth.user);
  const { data, error, isFetching, isLoading, refetch } = useGetDashboardSummaryQuery();
  const refresh = useCallback(() => {
    void refetch();
  }, [refetch]);
  const errorMessage = dashboardErrorMessage(error as ApiError | undefined,t);

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
          title={t('dashboard.unableTitle')}
        />
      </ScrollScreen>
    );
  }

  const metrics = buildDashboardMetrics(data).map(metric=>({
    ...metric,
    title:t(`dashboard.${metric.key==='documents'?'totalDocuments':metric.key==='messages'?'messagesThisMonth':metric.key==='storage'?'storageUsed':'activeUsers'}`),
    value:metric.key==='documents'?new Intl.NumberFormat(locale).format(data.totalDocuments):metric.key==='messages'?new Intl.NumberFormat(locale).format(data.userMessagesThisMonth):metric.key==='storage'?formatBytes(data.storedDocumentBytes):new Intl.NumberFormat(locale).format(data.activeUsers),
    detail:metric.key==='documents'?(data.documentsAddedThisWeek?t('dashboard.documentsThisWeek',{count:new Intl.NumberFormat(locale).format(data.documentsAddedThisWeek)}):t('dashboard.noDocumentsThisWeek')):metric.key==='messages'?(data.userMessagesPreviousMonth<=0?(data.userMessagesThisMonth?t('dashboard.newActivity'):t('dashboard.noMessages')):t('dashboard.versusLastMonth',{percentage:`${Math.round(((data.userMessagesThisMonth-data.userMessagesPreviousMonth)/data.userMessagesPreviousMonth)*100)>0?'+':''}${Math.round(((data.userMessagesThisMonth-data.userMessagesPreviousMonth)/data.userMessagesPreviousMonth)*100)}`})):metric.key==='storage'?(data.storageLimitBytes?t('dashboard.ofStorage',{limit:formatBytes(data.storageLimitBytes)}):t('dashboard.noStorageLimit')):(data.activeUsersAddedThisWeek?t('dashboard.usersThisWeek',{count:new Intl.NumberFormat(locale).format(data.activeUsersAddedThisWeek)}):t('dashboard.noUsersThisWeek')),
  }));
  const shortcuts = dashboardShortcutsForRole(user?.role).map(shortcut=>{const key=shortcut.href==='/chat'?'Chat':String(shortcut.href).includes('documents/upload')?'Upload':String(shortcut.href).includes('conversations')?'Conversations':'Tickets';return {...shortcut,title:t(`dashboard.shortcut${key}`),description:t(`dashboard.shortcut${key}Description`)}});
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
              {t('dashboard.welcome',{name:firstName(user?.fullName)??t('dashboard.fallbackName')})}
            </AppText>
            <AppText muted>{t('dashboard.description')}</AppText>
          </View>
          <Badge tone="primary">{user?.plan ?? t('common.currentPlan')}</Badge>
        </View>
      </View>

      {error ? (
        <RetryPanel
          description={errorMessage}
          onRetry={refresh}
          title={t('dashboard.staleTitle')}
        />
      ) : null}

      <Card elevated style={styles.section}>
        <AppText accessibilityRole="header" variant="heading">{t('dashboard.quickActions')}</AppText>
        <View>
          {shortcuts.map((shortcut, index) => (
            <Fragment key={shortcut.title}>
              {index > 0 ? <Separator /> : null}
              <ListRow
                accessibilityHint={t('dashboard.openHint',{title:shortcut.title})}
                onPress={() => router.push(shortcut.href)}
                subtitle={shortcut.description}
                title={shortcut.title}
                trailing={<Badge>{t('common.open')}</Badge>}
              />
            </Fragment>
          ))}
        </View>
      </Card>

      <View style={styles.sectionHeading}>
        <AppText accessibilityRole="header" variant="heading">{t('dashboard.overview')}</AppText>
        <AppText muted variant="bodySmall">{t('dashboard.overviewDescription')}</AppText>
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
          <AppText accessibilityRole="header" variant="heading">{t('dashboard.recentDocuments')}</AppText>
          <AppText muted variant="bodySmall">{t('dashboard.recentDescription')}</AppText>
        </View>
        {data.recentDocuments.length === 0 ? (
          <EmptyState
            actionLabel={t('dashboard.uploadFirst')}
            description={t('dashboard.noDocumentsDescription')}
            onAction={() => router.push('/documents/upload' as Href)}
            title={t('dashboard.noDocumentsTitle')}
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
  const {t,i18n}=useTranslation();const locale=i18n.resolvedLanguage?.startsWith('vi')?'vi-VN':'en-US';
  const status = documentStatusPresentation(document.status);
  return (
    <ListRow
      accessibilityHint={t('dashboard.documentHint')}
      onPress={onPress}
      subtitle={`${document.fileType} · ${formatBytes(document.fileSizeBytes)} · ${formatDashboardDate(document.uploadedAt,locale,t('dashboard.unknownDate'))}`}
      title={document.fileName}
      trailing={<Badge tone={status.tone}>{t(`dashboard.status.${document.status.toLowerCase()}`)}</Badge>}
    />
  );
}

function firstName(fullName?: string): string | null {
  return fullName?.trim().split(/\s+/)[0] || null;
}

function dashboardErrorMessage(error:ApiError|undefined,t:(key:string)=>string): string {
  if (error?.kind === 'network' || error?.kind === 'timeout') return error.message;
  return t('dashboard.unavailable');
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

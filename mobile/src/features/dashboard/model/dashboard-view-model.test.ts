import {
  buildDashboardMetrics,
  dashboardShortcutsForRole,
  documentStatusPresentation,
  formatBytes,
  formatDashboardDate,
  messageTrend,
  storagePercentage,
} from '@/features/dashboard/model/dashboard-view-model';
import type { DashboardSummary } from '@/features/dashboard/types';

const summary: DashboardSummary = {
  activeUsers: 12,
  activeUsersAddedThisWeek: 2,
  documentsAddedThisWeek: 3,
  recentDocuments: [],
  storageLimitBytes: 10 * 1024 * 1024,
  storedDocumentBytes: 2 * 1024 * 1024,
  totalDocuments: 25,
  userMessagesPreviousMonth: 80,
  userMessagesThisMonth: 100,
};

describe('dashboard view model', () => {
  it('maps the current response into the four supported metrics', () => {
    expect(buildDashboardMetrics(summary)).toEqual([
      { detail: '+3 this week', key: 'documents', title: 'Total documents', value: '25' },
      { detail: '+25% vs last month', key: 'messages', title: 'Messages this month', value: '100' },
      { detail: 'of 10.0 MB', key: 'storage', title: 'Storage used', value: '2.0 MB' },
      { detail: '+2 this week', key: 'users', title: 'Active users', value: '12' },
    ]);
  });

  it('handles zero baselines, byte boundaries, and storage limits safely', () => {
    expect(messageTrend(0, 0)).toBe('No messages this month');
    expect(messageTrend(4, 0)).toBe('New activity this month');
    expect(messageTrend(5, 10)).toBe('-50% vs last month');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(storagePercentage(50, 0)).toBe(0);
    expect(storagePercentage(150, 100)).toBe(100);
  });

  it('formats dates and current document statuses deterministically', () => {
    expect(formatDashboardDate('2026-07-14T08:30:00')).toBe('Jul 14, 2026');
    expect(formatDashboardDate('not-a-date')).toBe('Unknown date');
    expect(documentStatusPresentation('PENDING')).toEqual({ label: 'Pending', tone: 'warning' });
    expect(documentStatusPresentation('PROCESSING')).toEqual({ label: 'Processing', tone: 'primary' });
    expect(documentStatusPresentation('COMPLETED')).toEqual({ label: 'Completed', tone: 'success' });
    expect(documentStatusPresentation('FAILED')).toEqual({ label: 'Failed', tone: 'danger' });
  });

  it.each(['USER', 'TENANT_ADMIN'])('keeps all workflow shortcuts available to %s', (role) => {
    expect(dashboardShortcutsForRole(role).map((shortcut) => shortcut.title)).toEqual([
      'Chat',
      'Upload document',
      'Conversations',
      'Tickets',
    ]);
  });
});

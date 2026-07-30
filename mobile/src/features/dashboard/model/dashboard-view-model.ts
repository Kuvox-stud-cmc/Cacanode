import type { Href } from 'expo-router';

import type {
  DashboardSummary,
  DocumentStatus,
} from '@/features/dashboard/types';

export type DashboardMetric = {
  detail: string;
  key: 'documents' | 'messages' | 'storage' | 'users';
  title: string;
  value: string;
};

export type DashboardShortcut = {
  description: string;
  href: Href;
  title: string;
};

export function formatCount(value: number,locale='en-US'): string {
  return new Intl.NumberFormat(locale).format(Math.max(0, value));
}

export function formatBytes(bytes: number): string {
  const safeBytes = Math.max(0, bytes);
  if (safeBytes < 1024) return `${safeBytes} B`;
  if (safeBytes < 1024 ** 2) return `${(safeBytes / 1024).toFixed(1)} KB`;
  if (safeBytes < 1024 ** 3) return `${(safeBytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(safeBytes / 1024 ** 3).toFixed(1)} GB`;
}

export function formatDashboardDate(isoDate: string,locale='en-US',unknown='Unknown date'): string {
  const hasTimeZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(isoDate);
  const date = new Date(hasTimeZone ? isoDate : `${isoDate}Z`);
  if (Number.isNaN(date.getTime())) return unknown;
  return new Intl.DateTimeFormat(locale, {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
    year: 'numeric',
  }).format(date);
}

export function messageTrend(current: number, previous: number): string {
  if (previous <= 0) {
    return current > 0 ? 'New activity this month' : 'No messages this month';
  }
  const percentage = Math.round(((current - previous) / previous) * 100);
  return `${percentage > 0 ? '+' : ''}${percentage}% vs last month`;
}

export function storagePercentage(used: number, limit: number): number {
  if (limit <= 0) return 0;
  return Math.min(100, Math.max(0, Math.round((used / limit) * 100)));
}

export function buildDashboardMetrics(summary: DashboardSummary): DashboardMetric[] {
  return [
    {
      detail:
        summary.documentsAddedThisWeek > 0
          ? `+${formatCount(summary.documentsAddedThisWeek)} this week`
          : 'No documents added this week',
      key: 'documents',
      title: 'Total documents',
      value: formatCount(summary.totalDocuments),
    },
    {
      detail: messageTrend(summary.userMessagesThisMonth, summary.userMessagesPreviousMonth),
      key: 'messages',
      title: 'Messages this month',
      value: formatCount(summary.userMessagesThisMonth),
    },
    {
      detail:
        summary.storageLimitBytes > 0
          ? `of ${formatBytes(summary.storageLimitBytes)}`
          : 'No storage limit configured',
      key: 'storage',
      title: 'Storage used',
      value: formatBytes(summary.storedDocumentBytes),
    },
    {
      detail:
        summary.activeUsersAddedThisWeek > 0
          ? `+${formatCount(summary.activeUsersAddedThisWeek)} this week`
          : 'No users added this week',
      key: 'users',
      title: 'Active users',
      value: formatCount(summary.activeUsers),
    },
  ];
}

const dashboardShortcuts: DashboardShortcut[] = [
  { title: 'Chat', description: 'Ask a question in the employee workspace', href: '/chat' },
  {
    title: 'Upload document',
    description: 'Add knowledge for employees or customers',
    href: '/documents/upload' as Href,
  },
  {
    title: 'Conversations',
    description: 'Review recent customer conversations',
    href: '/conversations' as Href,
  },
  { title: 'Tickets', description: 'Review customer support tickets', href: '/tickets' },
];

export function dashboardShortcutsForRole(_role: string | undefined): DashboardShortcut[] {
  return dashboardShortcuts;
}

export function documentStatusPresentation(status: DocumentStatus): {
  label: string;
  tone: 'primary' | 'success' | 'warning' | 'danger';
} {
  const statuses = {
    PENDING: { label: 'Pending', tone: 'warning' },
    PROCESSING: { label: 'Processing', tone: 'primary' },
    COMPLETED: { label: 'Completed', tone: 'success' },
    FAILED: { label: 'Failed', tone: 'danger' },
  } as const;
  return statuses[status];
}

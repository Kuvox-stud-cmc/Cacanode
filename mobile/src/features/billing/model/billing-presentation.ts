import type { BadgeTone } from '@/components/ui/badge';

export type BillingPresentation = {
  label: string;
  tone: BadgeTone;
};

export function getBillingPresentation(
  plan: string | null | undefined,
  status?: string | null,
): BillingPresentation {
  if (status?.trim().toUpperCase() === 'GRACE') {
    return {
      label: plan?.trim().toUpperCase() === 'BUSINESS' ? 'Business · Grace' : 'Pro · Grace',
      tone: 'danger',
    };
  }

  switch (plan?.trim().toUpperCase()) {
    case 'TRIAL':
      return { label: 'Trial', tone: 'warning' };
    case 'FREE':
    case 'STARTER':
      return { label: 'Starter', tone: 'neutral' };
    case 'PRO':
      return { label: 'Pro', tone: 'primary' };
    case 'BUSINESS':
      return { label: 'Business', tone: 'success' };
    case 'ENTERPRISE':
      return { label: 'Enterprise', tone: 'success' };
    default:
      return { label: 'Current plan', tone: 'neutral' };
  }
}

export function formatBillingDate(value: string | null | undefined): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

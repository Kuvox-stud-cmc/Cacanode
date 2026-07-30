import { Badge } from '@/components/ui/badge';
import { getBillingPresentation } from '@/features/billing/model/billing-presentation';
import type { BillingAccount } from '@/features/billing/types';
import { useTranslation } from 'react-i18next';

export function PlanStatusBadge({
  account,
  fallbackPlan,
}: {
  account?: BillingAccount | null;
  fallbackPlan?: string | null;
}) {
  const {t}=useTranslation();
  const presentation = getBillingPresentation(
    account?.planCode ?? fallbackPlan,
    account?.status,
  );
  const plan=(account?.planCode??fallbackPlan)?.trim().toUpperCase();
  const base=plan==='TRIAL'?t('billing.trial'):plan==='STARTER'||plan==='FREE'?t('billing.starter'):plan==='PRO'?t('billing.pro'):plan==='BUSINESS'?t('billing.business'):plan==='ENTERPRISE'?t('billing.enterprise'):t('common.currentPlan');
  return <Badge tone={presentation.tone}>{account?.status==='GRACE'?`${base} · ${t('billing.grace')}`:base}</Badge>;
}

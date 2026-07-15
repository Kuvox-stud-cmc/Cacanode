import { Badge } from '@/components/ui/badge';
import { getBillingPresentation } from '@/features/billing/model/billing-presentation';
import type { BillingAccount } from '@/features/billing/types';

export function PlanStatusBadge({
  account,
  fallbackPlan,
}: {
  account?: BillingAccount | null;
  fallbackPlan?: string | null;
}) {
  const presentation = getBillingPresentation(
    account?.planCode ?? fallbackPlan,
    account?.status,
  );
  return <Badge tone={presentation.tone}>{presentation.label}</Badge>;
}

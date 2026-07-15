import { getBillingPresentation } from '@/features/billing/model/billing-presentation';

describe('getBillingPresentation', () => {
  it.each([
    ['TRIAL', 'TRIAL', 'Trial', 'warning'],
    ['STARTER', 'STARTER', 'Starter', 'neutral'],
    ['PRO', 'ACTIVE', 'Pro', 'primary'],
    ['PRO', 'GRACE', 'Pro · Grace', 'danger'],
    ['ENTERPRISE', 'ENTERPRISE', 'Enterprise', 'success'],
  ])('maps %s/%s to the expected plan visual', (plan, status, label, tone) => {
    expect(getBillingPresentation(plan, status)).toEqual({ label, tone });
  });

  it('uses a safe fallback for unknown plans', () => {
    expect(getBillingPresentation('UNKNOWN')).toEqual({
      label: 'Current plan',
      tone: 'neutral',
    });
  });
});

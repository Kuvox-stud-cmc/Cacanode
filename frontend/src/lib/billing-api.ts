import { getApiBase } from "@/lib/auth-api";
import { parseApiError, readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

export type BillingPlanCode = "STARTER" | "TRIAL" | "PRO" | "ENTERPRISE";
export type BillingInterval = "MONTHLY" | "ANNUAL";
export type BillingStatus = "TRIAL" | "STARTER" | "ACTIVE" | "GRACE" | "ENTERPRISE";
export type PaymentStatus = "PENDING" | "PROCESSING" | "PAID" | "CANCELLED" | "EXPIRED" | "FAILED" | "REVIEW";

export type BillingLimits = {
  messages: number | null;
  documents: number | null;
  teamMembers: number | null;
  storageMb: number | null;
};

export type BillingFeatures = {
  apiAccess: boolean;
  webhooks: boolean;
  advancedAnalytics: boolean;
  customBranding: boolean;
};

export type BillingPlan = {
  planCode: Exclude<BillingPlanCode, "TRIAL">;
  name: string;
  description: string;
  limits: BillingLimits;
  features: BillingFeatures;
  includedFeatures: string[];
  prices: Array<{
    interval: BillingInterval | null;
    amountVnd: number | null;
    currency: string;
    label: string;
  }>;
  contactSales: boolean;
  salesUrl: string | null;
  highlighted: boolean;
};

export type BillingPayment = {
  paymentId: string;
  status: PaymentStatus;
  planCode: BillingPlanCode;
  interval: BillingInterval;
  amountVnd: number;
  currency: string;
  checkoutUrl: string | null;
  expiresAt: string;
  paidAt: string | null;
  failureReason: string | null;
};

export type BillingUsage = { used: number; limit: number | null; overLimit: boolean };

export type BillingAccount = {
  planCode: BillingPlanCode;
  status: BillingStatus;
  interval: BillingInterval | null;
  trialEndsAt: string | null;
  paidThroughAt: string | null;
  graceEndsAt: string | null;
  quotaPeriodStart: string;
  nextQuotaResetAt: string;
  messages: BillingUsage;
  documents: BillingUsage;
  teamMembers: BillingUsage;
  storageMb: BillingUsage;
  features: BillingFeatures;
  pendingPayment: BillingPayment | null;
  cancelAtPeriodEnd: boolean;
};

export async function getPublicBillingPlans(): Promise<BillingPlan[]> {
  const response = await fetch(`${getApiBase()}/public/billing/plans`, { cache: "no-store" });
  return readJsonOrThrow(response);
}

export async function getBillingAccount(request: ApiRequest): Promise<BillingAccount> {
  return readJsonOrThrow(await request(`${getApiBase()}/billing/account`));
}

export async function createBillingCheckout(
  request: ApiRequest,
  interval: BillingInterval,
): Promise<{ paymentId: string; checkoutUrl: string; expiresAt: string }> {
  return readJsonOrThrow(await request(`${getApiBase()}/billing/checkouts`, {
    method: "POST",
    headers: { "Idempotency-Key": crypto.randomUUID() },
    body: JSON.stringify({ planCode: "PRO", interval }),
  }));
}

export async function getBillingPayment(request: ApiRequest, paymentId: string): Promise<BillingPayment> {
  return readJsonOrThrow(await request(`${getApiBase()}/billing/payments/${paymentId}`));
}

export async function downgradeBilling(request: ApiRequest): Promise<{
  scheduled: boolean;
  effectiveAt: string | null;
  account: BillingAccount;
}> {
  return readJsonOrThrow(await request(`${getApiBase()}/billing/downgrade`, { method: "POST" }));
}

export async function ensureOk(response: Response): Promise<void> {
  if (!response.ok) throw await parseApiError(response);
}

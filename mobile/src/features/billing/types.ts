export type BillingPlanCode = 'STARTER' | 'TRIAL' | 'PRO' | 'BUSINESS' | 'ENTERPRISE';
export type BillingInterval = 'MONTHLY' | 'ANNUAL';
export type BillingStatus = 'TRIAL' | 'STARTER' | 'ACTIVE' | 'GRACE' | 'ENTERPRISE';
export type PaymentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'PAID'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'FAILED'
  | 'REVIEW';

export type BillingUsage = {
  used: number;
  limit: number | null;
  overLimit: boolean;
};

export type HiringBillingUsage = {
  used: number;
  reserved: number;
  limit: number;
  overLimit: boolean;
};

export type BillingFeatures = {
  apiAccess: boolean;
  webhooks: boolean;
  advancedAnalytics: boolean;
  customBranding: boolean;
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
  activeJobs: HiringBillingUsage;
  verifiedApplications: HiringBillingUsage;
  interviewSeconds: HiringBillingUsage;
  cvAnalyses: HiringBillingUsage;
  recruitmentStorageBytes: HiringBillingUsage;
  features: BillingFeatures;
  pendingPayment: BillingPayment | null;
  cancelAtPeriodEnd: boolean;
};

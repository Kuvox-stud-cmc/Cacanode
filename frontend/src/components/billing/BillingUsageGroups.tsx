"use client";

import { Progress } from "@/components/ui/progress";
import type { BillingAccount, BillingUsage, HiringBillingUsage } from "@/lib/billing-api";

export function usagePercent(usage: BillingUsage): number {
  if (usage.limit === null || usage.limit === 0) return 0;
  return Math.min(100, Math.round((usage.used / usage.limit) * 100));
}

export function hiringUsagePercent(usage: HiringBillingUsage): number {
  if (usage.limit === 0) return usage.used + usage.reserved > 0 ? 100 : 0;
  return Math.min(100, Math.round(((usage.used + usage.reserved) / usage.limit) * 100));
}

type BillingUsageGroupsProps = {
  account: BillingAccount;
  copy: {
    platformUsage: string;
    hiringUsage: string;
    unlimited: string;
    overLimit: string;
    reserved: (amount: string) => string;
    labels: {
      messages: string;
      documents: string;
      teamMembers: string;
      storage: string;
      activeJobs: string;
      verifiedApplications: string;
      interviews: string;
      cvAnalyses: string;
      recruitmentStorage: string;
    };
  };
  formatNumber: (value: number) => string;
  formatBytes: (value: number) => string;
};

export function BillingUsageGroups({
  account,
  copy,
  formatNumber,
  formatBytes,
}: BillingUsageGroupsProps) {
  const usageLabel = (usage: BillingUsage, suffix = "") =>
    `${formatNumber(usage.used)}${suffix} / ${usage.limit === null ? copy.unlimited : `${formatNumber(usage.limit)}${suffix}`}`;
  const hiringUsageLabel = (usage: HiringBillingUsage, formatter: (value: number) => string) =>
    `${formatter(usage.used)} / ${formatter(usage.limit)}`;
  const platform = [
    { label: copy.labels.messages, usage: account.messages, suffix: "" },
    { label: copy.labels.documents, usage: account.documents, suffix: "" },
    { label: copy.labels.teamMembers, usage: account.teamMembers, suffix: "" },
    { label: copy.labels.storage, usage: account.storageMb, suffix: " MB" },
  ];
  const hiring = [
    { label: copy.labels.activeJobs, usage: account.activeJobs, formatValue: formatNumber },
    { label: copy.labels.verifiedApplications, usage: account.verifiedApplications, formatValue: formatNumber },
    { label: copy.labels.interviews, usage: account.interviewSeconds, formatValue: (value: number) => `${formatNumber(value / 60)} min` },
    { label: copy.labels.cvAnalyses, usage: account.cvAnalyses, formatValue: formatNumber },
    { label: copy.labels.recruitmentStorage, usage: account.recruitmentStorageBytes, formatValue: formatBytes },
  ];

  return (
    <>
      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-900">{copy.platformUsage}</h3>
        <div className="grid gap-4 sm:grid-cols-2">
          {platform.map((quota) => (
            <div key={quota.label} className={`rounded-lg border p-4 ${quota.usage.overLimit ? "border-red-200 bg-red-50" : "border-slate-200 bg-slate-50"}`}>
              <div className="mb-3 flex items-center justify-between gap-3">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{quota.label}</p>
                <p className="text-sm font-semibold text-slate-900">{usageLabel(quota.usage, quota.suffix)}</p>
              </div>
              <Progress value={usagePercent(quota.usage)} />
              {quota.usage.overLimit && <p className="mt-2 text-xs text-red-700">{copy.overLimit}</p>}
            </div>
          ))}
        </div>
      </div>
      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-900">{copy.hiringUsage}</h3>
        <div className="grid gap-4 sm:grid-cols-2">
          {hiring.map((quota) => (
            <div key={quota.label} className={`rounded-lg border p-4 ${quota.usage.overLimit ? "border-red-200 bg-red-50" : "border-slate-200 bg-slate-50"}`}>
              <div className="mb-3 flex items-center justify-between gap-3">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{quota.label}</p>
                <p className="text-sm font-semibold text-slate-900">{hiringUsageLabel(quota.usage, quota.formatValue)}</p>
              </div>
              <Progress value={hiringUsagePercent(quota.usage)} />
              {quota.usage.reserved > 0 && (
                <p className="mt-2 text-xs text-amber-700">
                  {copy.reserved(quota.formatValue(quota.usage.reserved))}
                </p>
              )}
              {quota.usage.overLimit && <p className="mt-2 text-xs text-red-700">{copy.overLimit}</p>}
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

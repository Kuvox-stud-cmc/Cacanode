import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BillingUsageGroups, hiringUsagePercent, usagePercent } from "@/components/billing/BillingUsageGroups";
import type { BillingAccount } from "@/lib/billing-api";

const account: BillingAccount = {
  planCode: "BUSINESS",
  status: "GRACE",
  interval: "ANNUAL",
  trialEndsAt: null,
  paidThroughAt: "2026-07-23T10:15:30",
  graceEndsAt: "2026-07-26T10:15:30",
  quotaPeriodStart: "2026-07-01T00:00:00",
  nextQuotaResetAt: "2026-08-01T00:00:00",
  messages: { used: 100, limit: 1_000, overLimit: false },
  documents: { used: 5, limit: 50, overLimit: false },
  teamMembers: { used: 16, limit: 15, overLimit: true },
  storageMb: { used: 100, limit: null, overLimit: false },
  activeJobs: { used: 0, reserved: 2, limit: 10, overLimit: false },
  verifiedApplications: { used: 100, reserved: 0, limit: 1_000, overLimit: false },
  interviewSeconds: { used: 3_600, reserved: 600, limit: 18_000, overLimit: false },
  cvAnalyses: { used: 50, reserved: 0, limit: 500, overLimit: false },
  recruitmentStorageBytes: { used: 1_073_741_824, reserved: 536_870_912, limit: 10_737_418_240, overLimit: false },
  features: { apiAccess: true, webhooks: true, advancedAnalytics: true, customBranding: true },
  pendingPayment: null,
  cancelAtPeriodEnd: false,
};

describe("BillingUsageGroups", () => {
  it("renders separate platform and hiring groups with pending interview and storage reservations", () => {
    render(
      <BillingUsageGroups
        account={account}
        copy={{
          platformUsage: "Platform usage",
          hiringUsage: "Hiring usage",
          unlimited: "Unlimited",
          overLimit: "Over limit",
          reserved: (amount) => `${amount} reserved`,
          labels: {
            messages: "Messages",
            documents: "Documents",
            teamMembers: "Team members",
            storage: "Platform storage",
            activeJobs: "Active jobs",
            verifiedApplications: "Verified applications",
            interviews: "Interviews",
            cvAnalyses: "CV analyses",
            recruitmentStorage: "Recruitment storage",
          },
        }}
        formatNumber={(value) => String(value)}
        formatBytes={(value) => `${value / 1024 ** 2} MB`}
      />,
    );

    expect(screen.getByRole("heading", { name: "Platform usage" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Hiring usage" })).toBeInTheDocument();
    expect(screen.getByText("10 min reserved")).toBeInTheDocument();
    expect(screen.getByText("512 MB reserved")).toBeInTheDocument();
    expect(screen.getByText(/Unlimited/)).toBeInTheDocument();
    expect(screen.getByText("Over limit")).toBeInTheDocument();
  });

  it("calculates capped platform and reserved-inclusive hiring percentages", () => {
    expect(usagePercent({ used: 11, limit: 10, overLimit: true })).toBe(100);
    expect(usagePercent({ used: 1, limit: null, overLimit: false })).toBe(0);
    expect(hiringUsagePercent({ used: 4, reserved: 1, limit: 10, overLimit: false })).toBe(50);
    expect(hiringUsagePercent({ used: 1, reserved: 0, limit: 0, overLimit: true })).toBe(100);
  });
});

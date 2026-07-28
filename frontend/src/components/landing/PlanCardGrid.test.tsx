import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PlanCardGrid from "@/components/landing/PlanCardGrid";
import type { BillingPlan } from "@/lib/billing-api";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string, values?: Record<string, unknown>) => {
    const labels: Record<string, string> = {
      monthly: "Monthly",
      annual: "Annual",
      mostPopular: "Most popular",
      contracted: "Contracted",
      free: "Free",
      contactSales: "Contact sales",
      unavailable: "Unavailable",
      currentPlan: "Current plan",
      startFree: "Start free",
    };
    if (key === "priceMonthly" || key === "priceAnnual") return String(values?.amount);
    if (key === "renewPlan") return `Renew ${values?.plan}`;
    if (key === "choosePlan") return `Choose ${values?.plan}`;
    if (key === "getPlan") return `Get ${values?.plan}`;
    if (key.startsWith("limits.")) return `${key}:${values?.count}`;
    return labels[key] ?? key;
  },
  useFormatter: () => ({ number: (value: number) => new Intl.NumberFormat("en-US").format(value) }),
}));

vi.mock("@/i18n/navigation", () => ({
  Link: ({ children, href, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

const limits = {
  messages: 10_000,
  documents: 50,
  teamMembers: 5,
  storageMb: 10_240,
  activeJobs: 3,
  verifiedApplications: 150,
  interviewSeconds: 3_600,
  cvAnalyses: 100,
  recruitmentStorageBytes: 1_073_741_824,
};

const plans: BillingPlan[] = [
  plan("STARTER", "Starter", false, [{ interval: null, amountVnd: 0, currency: "VND", label: "Free" }]),
  plan("PRO", "Pro", true),
  plan("BUSINESS", "Business", false),
  { ...plan("ENTERPRISE", "Enterprise", false, []), contactSales: true, salesUrl: "mailto:sales@example.com" },
];

describe("PlanCardGrid", () => {
  it("renders four pricing cards, highlights Pro, and shows Enterprise hiring limits as contracted", () => {
    const { container } = render(
      <PlanCardGrid plans={plans} interval="MONTHLY" onIntervalChange={() => undefined} />,
    );

    expect(screen.getAllByRole("heading", { level: 3 })).toHaveLength(4);
    expect(screen.getByText("Most popular")).toBeInTheDocument();
    expect(screen.getAllByText(/Contracted/)).toHaveLength(5);
    expect(container.querySelector(".ring-2")).toHaveTextContent("Pro");
  });

  it("selects and renews Business with the chosen annual interval", () => {
    const onIntervalChange = vi.fn();
    const onSelectPlan = vi.fn();
    const { rerender } = render(
      <PlanCardGrid
        plans={plans}
        currentPlan="pro"
        interval="MONTHLY"
        onIntervalChange={onIntervalChange}
        onSelectPlan={onSelectPlan}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Annual" }));
    expect(onIntervalChange).toHaveBeenCalledWith("ANNUAL");

    rerender(
      <PlanCardGrid
        plans={plans}
        currentPlan="business"
        interval="ANNUAL"
        onIntervalChange={onIntervalChange}
        onSelectPlan={onSelectPlan}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Renew Business" }));
    expect(onSelectPlan).toHaveBeenCalledWith("business", "ANNUAL");
  });
});

function plan(
  planCode: BillingPlan["planCode"],
  name: string,
  highlighted: boolean,
  prices: BillingPlan["prices"] = [
    { interval: "MONTHLY", amountVnd: 1_000, currency: "VND", label: "monthly" },
    { interval: "ANNUAL", amountVnd: 10_000, currency: "VND", label: "annual" },
  ],
): BillingPlan {
  return {
    planCode,
    name,
    description: `${name} description`,
    limits,
    features: { apiAccess: true, webhooks: true, advancedAnalytics: true, customBranding: true },
    includedFeatures: [],
    prices,
    contactSales: false,
    salesUrl: null,
    highlighted,
  };
}

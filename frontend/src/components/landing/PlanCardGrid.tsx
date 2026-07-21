"use client";

import { Link } from "@/i18n/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { buttonVariants } from "@/components/ui/button";
import type { BillingInterval, BillingPlan } from "@/lib/billing-api";

export type PlanId = "starter" | "pro" | "enterprise";

type PlanCardGridProps = {
  plans: BillingPlan[];
  currentPlan?: PlanId | null;
  interval: BillingInterval;
  onIntervalChange: (interval: BillingInterval) => void;
  onSelectPlan?: (plan: PlanId, interval: BillingInterval) => void;
};

export function normalizePlanId(plan: string | undefined): PlanId | null {
  switch (plan?.trim().toLowerCase()) {
    case "free":
    case "starter": return "starter";
    case "pro": return "pro";
    case "enterprise": return "enterprise";
    default: return null;
  }
}

function publicHref(plan: BillingPlan): string {
  if (plan.planCode === "STARTER") return "/register";
  if (plan.planCode === "PRO") return "/register?plan=pro";
  return plan.salesUrl ?? "mailto:sales@cacanode.com";
}

export default function PlanCardGrid({
  plans, currentPlan, interval, onIntervalChange, onSelectPlan,
}: PlanCardGridProps) {
  const t = useTranslations("Pricing")
  const format = useFormatter()

  function formatLimit(value: number | null, suffix = ""): string {
    return value === null ? t("unlimited") : `${format.number(value)}${suffix}`;
  }

  function priceLabel(plan: BillingPlan): string {
    if (plan.planCode === "STARTER") return t("free");
    if (plan.planCode === "ENTERPRISE") return t("contactSales");
    const price = plan.prices.find((item) => item.interval === interval);
    return price?.amountVnd == null
      ? t("unavailable")
      : t(interval === "MONTHLY" ? "priceMonthly" : "priceAnnual", { amount: format.number(price.amountVnd) });
  }
  return (
    <div className="space-y-8">
      <div className="flex justify-center">
        <div className="inline-flex rounded-full border border-slate-200 bg-white p-1 shadow-sm">
          {(["MONTHLY", "ANNUAL"] as BillingInterval[]).map((value) => (
            <button key={value} type="button" onClick={() => onIntervalChange(value)}
              className={`rounded-full px-4 py-2 text-sm font-medium ${interval === value ? "bg-indigo-600 text-white" : "text-slate-600"}`}>
              {value === "MONTHLY" ? t("monthly") : t("annual")}
            </button>
          ))}
        </div>
      </div>
      <div className="grid grid-cols-1 items-stretch gap-6 md:grid-cols-3">
        {plans.map((plan) => {
          const planId = normalizePlanId(plan.planCode);
          const isCurrent = planId !== null && currentPlan === planId;
          const features = [
            t("limits.messages", { count: formatLimit(plan.limits.messages) }),
            t("limits.documents", { count: formatLimit(plan.limits.documents) }),
            t("limits.teamMembers", { count: formatLimit(plan.limits.teamMembers) }),
            t("limits.storage", { count: formatLimit(plan.limits.storageMb, " MB") }),
            ...plan.includedFeatures,
          ];
          return (
            <div key={plan.planCode} className={`relative flex flex-col rounded-2xl border bg-white p-7 ${plan.highlighted ? "border-indigo-500 shadow-xl shadow-indigo-100 ring-2 ring-indigo-500" : "border-slate-200"}`}>
              {plan.highlighted && <div className="absolute -top-3 left-1/2 -translate-x-1/2"><span className="rounded-full bg-indigo-600 px-3 py-1 text-xs font-semibold text-white">{t("mostPopular")}</span></div>}
              <div className="mb-6">
                <h3 className="mb-1 text-lg font-bold text-slate-900">{plan.name}</h3>
                <div className="mb-2 text-2xl font-bold text-slate-900">{priceLabel(plan)}</div>
                <p className="text-sm text-slate-500">{plan.description}</p>
              </div>
              <ul className="mb-7 flex-1 space-y-3">
                {features.map((feature) => <li key={feature} className="flex items-start gap-2.5"><Check className="mt-0.5 size-4 shrink-0 text-indigo-600" /><span className="text-sm text-slate-700">{feature}</span></li>)}
              </ul>
              {onSelectPlan && planId ? (
                <Button type="button" className="w-full" variant={isCurrent ? "outline" : "default"}
                  disabled={isCurrent && planId !== "pro"} onClick={() => onSelectPlan(planId, interval)}>
                  {isCurrent && planId !== "pro" ? t("currentPlan") : planId === "pro" && isCurrent ? t("renewPro") : t("choosePlan", { plan: plan.name })}
                </Button>
              ) : (
                <Link className={buttonVariants({ className: "w-full", variant: plan.planCode === "PRO" ? "default" : "outline" })}
                  href={publicHref(plan)}>{plan.contactSales ? t("contactSales") : plan.planCode === "STARTER" ? t("startFree") : t("getPro")}</Link>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

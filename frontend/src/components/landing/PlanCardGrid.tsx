"use client";

import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { mockPricingPlans } from "@/lib/mock-data";

export type PlanId = "starter" | "pro" | "enterprise";

type PlanCardGridProps = {
  currentPlan?: PlanId | null;
  onSelectPlan?: (plan: PlanId) => void;
};

export function normalizePlanId(plan: string | undefined): PlanId | null {
  switch (plan?.trim().toLowerCase()) {
    case "free":
    case "trial":
    case "starter":
      return "starter";
    case "pro":
      return "pro";
    case "enterprise":
      return "enterprise";
    default:
      return null;
  }
}

export default function PlanCardGrid({ currentPlan, onSelectPlan }: PlanCardGridProps) {
  return (
    <div className="grid grid-cols-1 items-stretch gap-6 md:grid-cols-3">
      {mockPricingPlans.map((plan) => {
        const planId = normalizePlanId(plan.name);
        const isCurrent = planId !== null && currentPlan === planId;

        return (
          <div
            key={plan.name}
            className={`relative flex flex-col rounded-2xl border bg-white p-7 ${
              plan.highlighted
                ? "border-indigo-500 shadow-xl shadow-indigo-100 ring-2 ring-indigo-500"
                : "border-slate-200"
            }`}
          >
            {plan.highlighted && (
              <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                <span className="rounded-full bg-indigo-600 px-3 py-1 text-xs font-semibold text-white">
                  Most Popular
                </span>
              </div>
            )}

            <div className="mb-6">
              <h3 className="mb-1 text-lg font-bold text-slate-900">{plan.name}</h3>
              <div className="mb-2">
                <span className="text-3xl font-bold text-slate-900">{plan.price}</span>
              </div>
              <p className="text-sm text-slate-500">{plan.description}</p>
            </div>

            <ul className={`flex-1 space-y-3 ${onSelectPlan ? "mb-7" : ""}`}>
              {plan.features.map((feature) => (
                <li key={feature} className="flex items-start gap-2.5">
                  <Check className="mt-0.5 size-4 shrink-0 text-indigo-600" />
                  <span className="text-sm text-slate-700">{feature}</span>
                </li>
              ))}
            </ul>

            {onSelectPlan && planId && (
              <Button
                type="button"
                className="w-full"
                variant={isCurrent ? "outline" : "default"}
                disabled={isCurrent}
                onClick={() => onSelectPlan(planId)}
              >
                {isCurrent ? "Current plan" : `Choose ${plan.name}`}
              </Button>
            )}
          </div>
        );
      })}
    </div>
  );
}

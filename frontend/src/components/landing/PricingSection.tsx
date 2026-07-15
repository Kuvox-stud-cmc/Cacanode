"use client";

import { useEffect, useState } from "react";
import PlanCardGrid from "@/components/landing/PlanCardGrid";
import { getPublicBillingPlans, type BillingInterval, type BillingPlan } from "@/lib/billing-api";

type PricingSectionProps = {
  title?: string;
  description?: string;
  compact?: boolean;
};

export default function PricingSection({
  title = "Simple, transparent pricing",
  description = "Start free, upgrade through secure PayOS checkout, or contact us for custom scale.",
  compact = false,
}: PricingSectionProps) {
  const [plans, setPlans] = useState<BillingPlan[]>([]);
  const [interval, setInterval] = useState<BillingInterval>("MONTHLY");

  useEffect(() => {
    getPublicBillingPlans().then(setPlans).catch(() => setPlans([]));
  }, []);

  return (
    <section id="pricing" className={compact ? "" : "px-4 py-20"}>
      <div className="max-w-6xl mx-auto">
        <div className={compact ? "mb-8" : "mb-14 text-center"}>
          <h2 className="text-3xl font-bold text-slate-900 mb-4">
            {title}
          </h2>
          <p className={`text-lg text-slate-500 ${compact ? "max-w-2xl" : "mx-auto max-w-xl"}`}>
            {description}
          </p>
        </div>

        {plans.length > 0 && <PlanCardGrid plans={plans} interval={interval} onIntervalChange={setInterval} />}
      </div>
    </section>
  );
}

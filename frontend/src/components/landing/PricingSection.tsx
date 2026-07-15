import PlanCardGrid from "@/components/landing/PlanCardGrid";

type PricingSectionProps = {
  title?: string;
  description?: string;
  compact?: boolean;
};

export default function PricingSection({
  title = "Simple, transparent pricing",
  description = "Plans are shown for comparison only. Plan changes are not available yet.",
  compact = false,
}: PricingSectionProps) {
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

        <PlanCardGrid />
      </div>
    </section>
  );
}

import PublicNavbar from "@/components/landing/PublicNavbar";
import PricingSection from "@/components/landing/PricingSection";
import Footer from "@/components/landing/Footer";
import { Check, X } from "lucide-react";

const comparisonRows = [
  { feature: "Messages / month", starter: "500", pro: "10,000", enterprise: "Unlimited" },
  { feature: "Documents", starter: "3", pro: "50", enterprise: "Unlimited" },
  { feature: "Team members", starter: "1", pro: "5", enterprise: "Unlimited" },
  { feature: "Custom branding", starter: false, pro: true, enterprise: true },
  { feature: "Advanced analytics", starter: false, pro: true, enterprise: true },
  { feature: "Webhook support", starter: false, pro: true, enterprise: true },
  { feature: "API access", starter: false, pro: true, enterprise: true },
  { feature: "Priority support", starter: false, pro: true, enterprise: true },
  { feature: "Dedicated CSM", starter: false, pro: false, enterprise: true },
  { feature: "SSO / SAML", starter: false, pro: false, enterprise: true },
  { feature: "SLA guarantee", starter: "None", pro: "99.9%", enterprise: "99.99%" },
];

const faqs = [
  {
    q: "Can I upgrade or downgrade at any time?",
    a: "Yes — plan changes take effect immediately. If you upgrade mid-cycle, you're prorated for the remaining days. Downgrading takes effect at the end of the current billing period.",
  },
  {
    q: "What happens when I hit the message limit?",
    a: "The widget will show a friendly message letting visitors know that automated support is temporarily unavailable. You won't be charged overages — just upgrade to get more messages.",
  },
  {
    q: "Is my data secure?",
    a: "Yes. All documents are encrypted at rest and in transit. We're SOC 2 Type II compliant and GDPR ready. Your data is never used to train shared models.",
  },
  {
    q: "Does CacaNode support multiple languages?",
    a: "Yes. CacaNode automatically responds in the visitor's language, supporting over 50 languages. Documents can be in any language.",
  },
  {
    q: "Can I try Pro features before paying?",
    a: "Yes! Every new account gets a 14-day Pro trial with no credit card required. After the trial ends you can stay on the free Starter plan or upgrade.",
  },
];

function Cell({ value }: { value: string | boolean }) {
  if (typeof value === "boolean") {
    return value ? (
      <Check className="w-4 h-4 text-indigo-600 mx-auto" />
    ) : (
      <X className="w-4 h-4 text-slate-300 mx-auto" />
    );
  }
  return <span className="text-sm text-slate-700">{value}</span>;
}

export default function PricingPage() {
  return (
    <div className="min-h-screen bg-white">
      <PublicNavbar />

      <div className="pt-20">
        <PricingSection />
      </div>

      {/* Comparison table */}
      <section className="py-16 px-4">
        <div className="max-w-4xl mx-auto">
          <h2 className="text-2xl font-bold text-slate-900 text-center mb-10">
            Full feature comparison
          </h2>

          <div className="border border-slate-200 rounded-2xl overflow-hidden">
            <table className="w-full">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="text-left px-5 py-3 text-sm font-medium text-slate-600 w-1/2">
                    Feature
                  </th>
                  <th className="text-center px-4 py-3 text-sm font-medium text-slate-700">Starter</th>
                  <th className="text-center px-4 py-3 text-sm font-semibold text-indigo-600">Pro</th>
                  <th className="text-center px-4 py-3 text-sm font-medium text-slate-700">Enterprise</th>
                </tr>
              </thead>
              <tbody>
                {comparisonRows.map((row, i) => (
                  <tr
                    key={row.feature}
                    className={`border-b border-slate-100 last:border-0 ${i % 2 === 0 ? "bg-white" : "bg-slate-50/50"}`}
                  >
                    <td className="px-5 py-3 text-sm text-slate-700">{row.feature}</td>
                    <td className="px-4 py-3 text-center">
                      <Cell value={row.starter} />
                    </td>
                    <td className="px-4 py-3 text-center bg-indigo-50/30">
                      <Cell value={row.pro} />
                    </td>
                    <td className="px-4 py-3 text-center">
                      <Cell value={row.enterprise} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section className="py-16 px-4 bg-slate-50">
        <div className="max-w-2xl mx-auto">
          <h2 className="text-2xl font-bold text-slate-900 text-center mb-10">
            Frequently asked questions
          </h2>
          <div className="space-y-3">
            {faqs.map(({ q, a }) => (
              <details
                key={q}
                className="bg-white border border-slate-200 rounded-xl group"
              >
                <summary className="flex items-center justify-between px-5 py-4 cursor-pointer text-sm font-medium text-slate-800 list-none">
                  {q}
                  <span className="text-slate-400 group-open:rotate-180 transition-transform text-lg leading-none ml-4">
                    ↓
                  </span>
                </summary>
                <p className="px-5 pb-4 text-sm text-slate-600 leading-relaxed">{a}</p>
              </details>
            ))}
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}

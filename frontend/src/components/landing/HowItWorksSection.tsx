import { Upload, Sliders, Code2 } from "lucide-react";
import { useTranslations } from "next-intl";

const steps = [
  {
    number: "1",
    icon: Upload,
    key: "upload" as const,
  },
  {
    number: "2",
    icon: Sliders,
    key: "configure" as const,
  },
  {
    number: "3",
    icon: Code2,
    key: "embed" as const,
  },
];

export default function HowItWorksSection() {
  const t = useTranslations("Landing")
  return (
    <section id="how-it-works" className="py-20 px-4">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-14">
          <h2 className="text-3xl font-bold text-slate-900 mb-4">
            {t("how.title")}
          </h2>
          <p className="text-slate-500 text-lg max-w-xl mx-auto">
            {t("how.description")}
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 relative">
          {/* Connector lines */}
          <div className="hidden md:block absolute top-10 left-1/3 right-1/3 h-px border-t-2 border-dashed border-indigo-200" />

          {steps.map(({ number, icon: Icon, key }) => (
            <div key={number} className="flex flex-col items-center text-center">
              <div className="relative mb-6">
                <div className="w-20 h-20 bg-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-indigo-200">
                  <Icon className="w-8 h-8 text-white" />
                </div>
                <div className="absolute -top-2 -right-2 w-7 h-7 bg-white border-2 border-indigo-600 rounded-full flex items-center justify-center text-indigo-600 text-xs font-bold">
                  {number}
                </div>
              </div>
              <h3 className="font-semibold text-slate-800 text-lg mb-3">{t(`how.items.${key}.title`)}</h3>
              <p className="text-sm text-slate-500 leading-relaxed max-w-xs">{t(`how.items.${key}.description`)}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

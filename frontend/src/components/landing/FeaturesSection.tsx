import { FileUp, Zap, Bot, BarChart2 } from "lucide-react";
import { useTranslations } from "next-intl";

const features = [
  {
    icon: FileUp,
    color: "bg-indigo-100 text-indigo-600",
    key: "upload" as const,
  },
  {
    icon: Zap,
    color: "bg-amber-100 text-amber-600",
    key: "deploy" as const,
  },
  {
    icon: Bot,
    color: "bg-violet-100 text-violet-600",
    key: "answers" as const,
  },
  {
    icon: BarChart2,
    color: "bg-emerald-100 text-emerald-600",
    key: "analytics" as const,
  },
];

export default function FeaturesSection() {
  const t = useTranslations("Landing")
  return (
    <section id="features" className="py-20 px-4 bg-slate-50">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-14">
          <h2 className="text-3xl font-bold text-slate-900 mb-4">
            {t("features.title")}
          </h2>
          <p className="text-slate-500 text-lg max-w-xl mx-auto">
            {t("features.description")}
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-6">
          {features.map(({ icon: Icon, color, key }) => (
            <div
              key={key}
              className="bg-white rounded-2xl border border-slate-200 p-6 hover:shadow-md transition-shadow"
            >
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center mb-4 ${color}`}>
                <Icon className="w-5 h-5" />
              </div>
              <h3 className="font-semibold text-slate-800 mb-2">{t(`features.items.${key}.title`)}</h3>
              <p className="text-sm text-slate-500 leading-relaxed">{t(`features.items.${key}.description`)}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

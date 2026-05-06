import { FileUp, Zap, Bot, BarChart2 } from "lucide-react";

const features = [
  {
    icon: FileUp,
    color: "bg-indigo-100 text-indigo-600",
    title: "Upload your knowledge base",
    description:
      "PDF, DOCX, TXT — drag and drop your documentation in seconds. Your bot learns everything instantly.",
  },
  {
    icon: Zap,
    color: "bg-amber-100 text-amber-600",
    title: "Deploy instantly",
    description:
      "Paste one JS snippet before your closing </body> tag. Your AI support widget goes live immediately.",
  },
  {
    icon: Bot,
    color: "bg-violet-100 text-violet-600",
    title: "Smart AI answers",
    description:
      "Powered by GPT-4o with RAG retrieval — always accurate, always grounded in your actual content.",
  },
  {
    icon: BarChart2,
    color: "bg-emerald-100 text-emerald-600",
    title: "Analytics built-in",
    description:
      "Track conversations, satisfaction rates, and popular questions. Know exactly how your bot is performing.",
  },
];

export default function FeaturesSection() {
  return (
    <section id="features" className="py-20 px-4 bg-slate-50">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-14">
          <h2 className="text-3xl font-bold text-slate-900 mb-4">
            Everything you need to automate support
          </h2>
          <p className="text-slate-500 text-lg max-w-xl mx-auto">
            No ML expertise required. No custom infrastructure. Just upload, configure, and ship.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-6">
          {features.map(({ icon: Icon, color, title, description }) => (
            <div
              key={title}
              className="bg-white rounded-2xl border border-slate-200 p-6 hover:shadow-md transition-shadow"
            >
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center mb-4 ${color}`}>
                <Icon className="w-5 h-5" />
              </div>
              <h3 className="font-semibold text-slate-800 mb-2">{title}</h3>
              <p className="text-sm text-slate-500 leading-relaxed">{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

import { Upload, Sliders, Code2 } from "lucide-react";

const steps = [
  {
    number: "1",
    icon: Upload,
    title: "Upload your documents",
    description:
      "Drag and drop PDFs, Word docs, or text files into your CacaNode dashboard. Your knowledge base is ready in seconds.",
  },
  {
    number: "2",
    icon: Sliders,
    title: "Configure your widget",
    description:
      "Pick a brand color, set a bot name, write a welcome message. See the changes live in our preview panel.",
  },
  {
    number: "3",
    icon: Code2,
    title: "Paste the snippet",
    description:
      "Copy one <script> tag into your website. Your AI support widget is live instantly — no build step needed.",
  },
];

export default function HowItWorksSection() {
  return (
    <section id="how-it-works" className="py-20 px-4">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-14">
          <h2 className="text-3xl font-bold text-slate-900 mb-4">
            Up and running in under 10 minutes
          </h2>
          <p className="text-slate-500 text-lg max-w-xl mx-auto">
            Three steps. No code. No DevOps. No waiting.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 relative">
          {/* Connector lines */}
          <div className="hidden md:block absolute top-10 left-1/3 right-1/3 h-px border-t-2 border-dashed border-indigo-200" />

          {steps.map(({ number, icon: Icon, title, description }) => (
            <div key={number} className="flex flex-col items-center text-center">
              <div className="relative mb-6">
                <div className="w-20 h-20 bg-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-indigo-200">
                  <Icon className="w-8 h-8 text-white" />
                </div>
                <div className="absolute -top-2 -right-2 w-7 h-7 bg-white border-2 border-indigo-600 rounded-full flex items-center justify-center text-indigo-600 text-xs font-bold">
                  {number}
                </div>
              </div>
              <h3 className="font-semibold text-slate-800 text-lg mb-3">{title}</h3>
              <p className="text-sm text-slate-500 leading-relaxed max-w-xs">{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

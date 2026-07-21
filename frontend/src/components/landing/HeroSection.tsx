"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { Sparkles, MessageSquare, Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import DemoModal from "./DemoModal";

const avatarColors = ["bg-indigo-500", "bg-violet-500", "bg-emerald-500", "bg-amber-500", "bg-rose-500"];
const avatarInitials = ["AC", "BT", "CR", "DK", "EL"];

export default function HeroSection() {
  const t = useTranslations("Landing");
  const [demoOpen, setDemoOpen] = useState(false);

  return (
    <section className="pt-28 pb-20 px-4 max-w-6xl mx-auto">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
        {/* Left — text */}
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 text-xs font-medium mb-6">
            <Sparkles className="w-3.5 h-3.5" />
            {t("hero.badge")}
          </div>

          <h1 className="text-4xl sm:text-5xl font-bold text-slate-900 leading-tight mb-5">
            {t("hero.title")}{" "}
            <span className="text-indigo-600">{t("hero.titleAccent")}</span>
          </h1>

          <p className="text-lg text-slate-500 leading-relaxed mb-8">
            {t("hero.description")}
          </p>

          <div className="flex flex-col sm:flex-row gap-3 mb-10">
            <Link href="/register">
              <Button
                size="lg"
                className="bg-indigo-600 hover:bg-indigo-700 text-white px-8"
              >
                {t("hero.startFree")}
              </Button>
            </Link>
            <Button
              size="lg"
              variant="outline"
              className="gap-2"
              onClick={() => setDemoOpen(true)}
            >
              <Play className="w-4 h-4 fill-current" />
              {t("hero.seeAction")}
            </Button>
          </div>

          {/* Social proof */}
          <div className="flex items-center gap-3">
            <div className="flex">
              {avatarColors.map((color, i) => (
                <div
                  key={i}
                  className={`w-8 h-8 rounded-full ${color} border-2 border-white flex items-center justify-center text-white text-xs font-medium -ml-2 first:ml-0`}
                >
                  {avatarInitials[i]}
                </div>
              ))}
            </div>
            <p className="text-sm text-slate-500">
              <span className="font-semibold text-slate-800">500+</span> {t("hero.socialProof")}
            </p>
          </div>
        </div>

        {/* Right — mock browser */}
        <div className="relative">
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
            {/* Browser chrome */}
            <div className="flex items-center gap-1.5 px-4 py-3 bg-slate-100 border-b border-slate-200">
              <div className="w-3 h-3 rounded-full bg-red-400" />
              <div className="w-3 h-3 rounded-full bg-yellow-400" />
              <div className="w-3 h-3 rounded-full bg-green-400" />
              <div className="flex-1 mx-3 bg-white rounded-md h-5 flex items-center px-2">
                <span className="text-slate-400 text-xs">acmecorp.com</span>
              </div>
            </div>

            {/* Page content */}
            <div className="relative bg-slate-50 p-5" style={{ height: "300px" }}>
              <div className="space-y-3">
                <div className="h-3 bg-slate-300 rounded w-40" />
                <div className="h-2 bg-slate-200 rounded" />
                <div className="h-2 bg-slate-200 rounded w-5/6" />
                <div className="h-2 bg-slate-200 rounded w-4/6" />
                <div className="flex gap-2 mt-3">
                  <div className="h-7 w-20 bg-indigo-600 rounded-md" />
                  <div className="h-7 w-20 bg-slate-200 rounded-md" />
                </div>
              </div>

              {/* Mini open widget */}
              <div className="absolute bottom-4 right-4 w-52">
                <div className="bg-white rounded-xl shadow-lg border border-slate-200 overflow-hidden text-xs">
                  <div className="bg-indigo-600 px-3 py-2 flex items-center gap-1.5">
                    <MessageSquare className="w-3 h-3 text-white" />
                    <span className="text-white font-medium text-xs">{t("demo.supportBot")}</span>
                    <span className="ml-auto text-indigo-200 text-xs">●</span>
                  </div>
                  <div className="p-2.5 space-y-2 bg-slate-50">
                    <div className="flex justify-start">
                      <div className="bg-white px-2 py-1.5 rounded-lg text-slate-700 shadow-sm max-w-[85%]">
                        {t("demo.greeting")}
                      </div>
                    </div>
                    <div className="flex justify-end">
                      <div className="bg-indigo-600 px-2 py-1.5 rounded-lg text-white max-w-[85%]">
                        {t("demo.question")}
                      </div>
                    </div>
                    <div className="flex justify-start">
                      <div className="bg-white px-2 py-1.5 rounded-lg text-slate-700 shadow-sm max-w-[90%]">
                        {t("demo.answer")}
                      </div>
                    </div>
                  </div>
                  <div className="px-2 py-1.5 bg-white border-t border-slate-100 flex gap-1">
                    <div className="flex-1 h-5 bg-slate-100 rounded" />
                    <div className="w-5 h-5 bg-indigo-600 rounded flex items-center justify-center">
                      <Send className="w-2.5 h-2.5 text-white" />
                    </div>
                  </div>
                </div>
              </div>

              {/* Floating badge */}
              <div className="absolute top-4 right-4 bg-white rounded-full px-3 py-1.5 shadow-md border border-slate-100 flex items-center gap-1.5 text-xs">
                <span className="w-2 h-2 rounded-full bg-green-500" />
                <span className="text-slate-700 font-medium">{t("demo.averageResponse")}</span>
              </div>
            </div>
          </div>

          {/* Decorative blob */}
          <div className="absolute -top-10 -right-10 w-64 h-64 bg-indigo-100 rounded-full blur-3xl opacity-50 -z-10" />
          <div className="absolute -bottom-10 -left-10 w-48 h-48 bg-violet-100 rounded-full blur-3xl opacity-50 -z-10" />
        </div>
      </div>

      <DemoModal open={demoOpen} onClose={() => setDemoOpen(false)} />
    </section>
  );
}

function Send({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
    </svg>
  );
}

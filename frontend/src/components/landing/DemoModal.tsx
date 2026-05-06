"use client";

import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import ChatWidgetPanel from "@/components/widget/ChatWidgetPanel";
import { X, Bot } from "lucide-react";

interface DemoModalProps {
  open: boolean;
  onClose: () => void;
}

export default function DemoModal({ open, onClose }: DemoModalProps) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      {/*
        max-w-5xl  = 1024px wide
        p-0        = remove default padding
        [&>button] = hide shadcn's built-in absolute close button; we render our own
      */}
      <DialogContent
        className="max-w-5xl w-[90vw] p-0 overflow-hidden flex flex-col gap-0 [&>button]:hidden"
        style={{ height: "82vh" }}
      >
        {/* a11y title (visually hidden) */}
        <DialogTitle className="sr-only">CacaNode live demo</DialogTitle>

        {/* Custom header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-200 shrink-0 bg-white">
          <div className="flex items-center gap-3">
            <div className="w-7 h-7 bg-indigo-600 rounded-md flex items-center justify-center shrink-0">
              <Bot className="w-4 h-4 text-white" />
            </div>
            <div>
              <p className="font-semibold text-slate-800 text-sm leading-tight">See CacaNode in action</p>
              <p className="text-xs text-slate-400 leading-tight">Type a message and watch the AI respond</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-md hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Body — two panels */}
        <div className="flex flex-1 overflow-hidden min-h-0">
          {/* Left — mock website (hidden on small screens) */}
          <div className="hidden md:flex flex-1 flex-col bg-slate-100 overflow-hidden relative">
            {/* Mock nav */}
            <div className="bg-white border-b border-slate-200 px-4 py-2.5 flex items-center justify-between shrink-0">
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 bg-emerald-600 rounded-sm" />
                <span className="font-semibold text-slate-700 text-xs">Acme Corp</span>
              </div>
              <div className="flex gap-4 text-xs text-slate-400">
                <span>Home</span>
                <span>Products</span>
                <span>Support</span>
                <span>Pricing</span>
              </div>
            </div>

            {/* Mock page content */}
            <div className="flex-1 overflow-hidden p-5 space-y-4">
              {/* Hero card */}
              <div className="bg-white rounded-xl p-5 shadow-sm">
                <div className="h-3 bg-slate-800 rounded-full w-2/3 mb-3" />
                <div className="space-y-1.5">
                  <div className="h-2 bg-slate-200 rounded-full" />
                  <div className="h-2 bg-slate-200 rounded-full w-5/6" />
                  <div className="h-2 bg-slate-200 rounded-full w-4/6" />
                </div>
                <div className="flex gap-2 mt-4">
                  <div className="h-7 w-24 bg-emerald-600 rounded-lg" />
                  <div className="h-7 w-24 bg-slate-100 rounded-lg border border-slate-200" />
                </div>
              </div>

              {/* Feature cards */}
              <div className="grid grid-cols-3 gap-3">
                {["#dbeafe", "#dcfce7", "#fef9c3"].map((bg, i) => (
                  <div key={i} className="bg-white rounded-xl p-4 shadow-sm">
                    <div className="w-6 h-6 rounded mb-2" style={{ backgroundColor: bg }} />
                    <div className="h-2 bg-slate-200 rounded-full w-2/3 mb-2" />
                    <div className="space-y-1">
                      <div className="h-1.5 bg-slate-100 rounded-full" />
                      <div className="h-1.5 bg-slate-100 rounded-full w-4/5" />
                    </div>
                  </div>
                ))}
              </div>

              {/* Table skeleton */}
              <div className="bg-white rounded-xl shadow-sm overflow-hidden">
                <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-100 flex gap-4">
                  {["w-1/3", "w-1/4", "w-1/5"].map((w, i) => (
                    <div key={i} className={`h-2 bg-slate-200 rounded-full ${w}`} />
                  ))}
                </div>
                {[1, 2, 3, 4].map((row) => (
                  <div key={row} className="px-4 py-2.5 border-b border-slate-50 flex gap-4 items-center last:border-0">
                    <div className="w-4 h-4 bg-slate-100 rounded-full shrink-0" />
                    <div className="h-2 bg-slate-100 rounded-full flex-1" />
                    <div className="h-2 bg-slate-100 rounded-full w-1/4" />
                    <div className="h-4 w-12 bg-emerald-100 rounded-full" />
                  </div>
                ))}
              </div>
            </div>

            {/* Bottom fade */}
            <div className="absolute bottom-0 inset-x-0 h-16 bg-gradient-to-t from-slate-100 to-transparent pointer-events-none" />
          </div>

          {/* Right — chat panel (fills full width on mobile) */}
          <div className="w-full md:w-80 lg:w-96 shrink-0 border-l border-slate-200 flex flex-col bg-white min-h-0">
            <ChatWidgetPanel
              fill
              alwaysOpen
              primaryColor="#4f46e5"
              botName="Support Bot"
            />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

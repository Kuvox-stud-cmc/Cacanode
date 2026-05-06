"use client";

import { useState, useMemo } from "react";
import { mockDailyVolume, mockPopularQuestions } from "@/lib/mock-data";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { MessageSquare, Clock, ThumbsUp, CheckCircle, TrendingUp } from "lucide-react";

const metricCards = [
  { label: "Total Conversations", value: "1,284", icon: MessageSquare, trend: "+12%", color: "text-indigo-600", bg: "bg-indigo-50" },
  { label: "Avg Response Time", value: "1.2s", icon: Clock, trend: "-0.3s", color: "text-violet-600", bg: "bg-violet-50" },
  { label: "Satisfaction Rate", value: "94%", icon: ThumbsUp, trend: "+2%", color: "text-emerald-600", bg: "bg-emerald-50" },
  { label: "Resolution Rate", value: "87%", icon: CheckCircle, trend: "+5%", color: "text-amber-600", bg: "bg-amber-50" },
];

export default function AnalyticsPage() {
  const [range, setRange] = useState<"7" | "30" | "90">("30");

  const slicedData = useMemo(() => {
    const days = parseInt(range);
    return mockDailyVolume.slice(-days);
  }, [range]);

  const maxCount = Math.max(...slicedData.map((d) => d.count));
  const maxQuestion = Math.max(...mockPopularQuestions.map((q) => q.count));

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold text-slate-800">Analytics</h2>

      {/* Metric cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {metricCards.map(({ label, value, icon: Icon, trend, color, bg }) => (
          <Card key={label}>
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-3">
                <span className="text-sm text-slate-500">{label}</span>
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${bg}`}>
                  <Icon className={`w-4 h-4 ${color}`} />
                </div>
              </div>
              <div className="text-2xl font-bold text-slate-800 mb-1">{value}</div>
              <div className="flex items-center gap-1 text-xs text-green-600">
                <TrendingUp className="w-3 h-3" />
                {trend} vs last period
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Bar chart */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base">Message Volume</CardTitle>
          <Select value={range} onValueChange={(v) => setRange(v as "7" | "30" | "90")}>
            <SelectTrigger className="w-36 h-8 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="7">Last 7 days</SelectItem>
              <SelectItem value="30">Last 30 days</SelectItem>
              <SelectItem value="90">Last 90 days</SelectItem>
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-1 h-40">
            {slicedData.map((d) => {
              const heightPct = (d.count / maxCount) * 100;
              const month = new Date(d.date).toLocaleString("default", { month: "short" });
              const day = new Date(d.date).getDate();
              return (
                <div key={d.date} className="relative group flex-1 flex flex-col items-center justify-end h-full">
                  {/* Tooltip */}
                  <div className="absolute bottom-full mb-1 hidden group-hover:block z-10">
                    <div className="bg-slate-800 text-white text-xs rounded px-2 py-1 whitespace-nowrap">
                      {month} {day}: {d.count} msgs
                    </div>
                  </div>
                  <div
                    className="w-full bg-indigo-500 hover:bg-indigo-600 rounded-t transition-colors cursor-pointer"
                    style={{ height: `${heightPct}%`, minHeight: "4px" }}
                  />
                </div>
              );
            })}
          </div>
          <div className="flex justify-between text-xs text-slate-400 mt-2 px-0.5">
            <span>
              {new Date(slicedData[0]?.date ?? "").toLocaleDateString("en-US", { month: "short", day: "numeric" })}
            </span>
            <span>
              {new Date(slicedData[slicedData.length - 1]?.date ?? "").toLocaleDateString("en-US", { month: "short", day: "numeric" })}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Popular questions */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Popular Questions</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {mockPopularQuestions.map(({ question, count }) => {
            const widthPct = (count / maxQuestion) * 100;
            return (
              <div key={question} className="relative rounded-lg overflow-hidden">
                <div
                  className="absolute inset-y-0 left-0 bg-indigo-50 rounded-lg"
                  style={{ width: `${widthPct}%` }}
                />
                <div className="relative flex items-center justify-between px-3 py-2.5">
                  <span className="text-sm text-slate-700 truncate pr-4">{question}</span>
                  <span className="text-sm font-semibold text-indigo-700 shrink-0">{count}</span>
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>
    </div>
  );
}

"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { listRecruitmentInterviews, listRecruitmentJobs, type RecruitmentInterview, type RecruitmentJob } from "@/lib/recruitment-admin-api";
import { Calendar, Clock, Eye, ChevronLeft, ChevronRight, UserCheck, Mic } from "lucide-react";

import { formatEnumLabel, formatTimezoneLabel } from "@/lib/recruitment-formatters";

export function SchedulePage() {
  const t = useTranslations("Recruitment");
  const locale = useLocale();
  const format = useFormatter();
  const { request } = useApiClient();

  const [interviews, setInterviews] = useState<RecruitmentInterview[]>([]);
  const [invitedQueue, setInvitedQueue] = useState<RecruitmentInterview[]>([]);
  const [jobs, setJobs] = useState<RecruitmentJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [selectedJobId, setSelectedJobId] = useState("ALL");
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [scheduledRes, invitedRes, jobsRes] = await Promise.all([
        listRecruitmentInterviews(request, { page: 0, size: 100, status: "SCHEDULED" }),
        listRecruitmentInterviews(request, { page: 0, size: 100, status: "INVITED" }),
        listRecruitmentJobs(request, { page: 0, size: 100 }),
      ]);
      setInterviews(scheduledRes.items);
      setInvitedQueue(invitedRes.items);
      setJobs(jobsRes.items);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [request, t]);

  useEffect(() => {
    // Client-side schedule data is loaded when API access changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  // Filter scheduled interviews for selected date
  const selectedDateStr = selectedDate.toISOString().slice(0, 10);
  const dateInterviews = interviews.filter((inv) => {
    if (selectedJobId !== "ALL" && inv.jobId !== selectedJobId) return false;
    if (!inv.scheduledStartAt) return false;
    return inv.scheduledStartAt.slice(0, 10) === selectedDateStr;
  });

  const nextDay = () => {
    const next = new Date(selectedDate);
    next.setDate(next.getDate() + 1);
    setSelectedDate(next);
  };

  const prevDay = () => {
    const prev = new Date(selectedDate);
    prev.setDate(prev.getDate() - 1);
    setSelectedDate(prev);
  };

  return (
    <div className="space-y-6" aria-live="polite">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">{t("nav.schedule")}</h3>
          <p className="text-sm text-muted-foreground">{t("pages.schedule")}</p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/recruitment/setup" />}>
          <Clock className="mr-1 h-4 w-4" />
          {t("nav.setup")}
        </Button>
      </div>

      {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3 bg-white p-3 rounded-lg border">
        <label className="text-sm font-medium">{locale.startsWith("vi") ? "Lọc theo công việc:" : "Filter Job:"}</label>
        <Select value={selectedJobId} onValueChange={(val) => setSelectedJobId(val ?? "ALL")}>
          <SelectTrigger className="w-64">
            <SelectValue>{selectedJobId === "ALL" ? (locale.startsWith("vi") ? "Tất cả tin tuyển dụng" : "All Jobs") : jobs.find(j => j.id === selectedJobId)?.title}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{locale.startsWith("vi") ? "Tất cả công việc" : "All Jobs"}</SelectItem>
            {jobs.map((j) => (
              <SelectItem key={j.id} value={j.id}>
                {j.title}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex items-center gap-2 ml-auto">
          <Button variant="outline" size="sm" onClick={prevDay}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm font-semibold min-w-44 text-center">
            {format.dateTime(selectedDate, { dateStyle: "full" })}
          </span>
          <Button variant="outline" size="sm" onClick={nextDay}>
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setSelectedDate(new Date())}>
            {locale.startsWith("vi") ? "Hôm nay" : "Today"}
          </Button>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        {/* Left 2 Cols: Daily Agenda Calendar */}
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Calendar className="h-4 w-4 text-indigo-600" /> {locale.startsWith("vi") ? "Lịch trình ngày " : "Agenda for "}{format.dateTime(selectedDate, { dateStyle: "medium" })}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <p className="p-6 text-center text-sm text-muted-foreground">{t("loading")}</p>
            ) : dateInterviews.length === 0 ? (
              <div className="text-center py-12 border-2 border-dashed rounded-lg">
                <Clock className="h-8 w-8 text-muted-foreground mx-auto mb-2 opacity-50" />
                <p className="text-sm font-medium text-slate-600">
                  {locale.startsWith("vi") ? "Không có buổi phỏng vấn nào vào ngày này." : "No interviews scheduled on this date."}
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  {locale.startsWith("vi") ? "Chọn ngày khác hoặc lên lịch cho ứng viên trong hàng chờ đã mời." : "Select another day or schedule candidates from the invited queue."}
                </p>
              </div>
            ) : (
              <div className="divide-y border rounded-lg">
                {dateInterviews.map((inv) => (
                  <div key={inv.id} className="p-4 flex items-center justify-between hover:bg-slate-50">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-indigo-700 text-sm">
                          {inv.scheduledStartAt ? format.dateTime(new Date(inv.scheduledStartAt), { timeStyle: "short" }) : "—"}
                        </span>
                        <strong className="text-base font-semibold">{inv.candidateName}</strong>
                        <Badge variant="outline">{formatEnumLabel(inv.status, locale)}</Badge>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {locale.startsWith("vi") ? "Tin tuyển dụng: " : "Job: "}{inv.jobTitle} · {locale.startsWith("vi") ? "Múi giờ: " : "Timezone: "}{formatTimezoneLabel(inv.schedulingTimezone)}
                      </p>
                    </div>
                    <Button size="sm" variant="outline" nativeButton={false} render={<Link href={`/recruitment/interviews/${inv.id}`} />}>
                      <Eye className="mr-1 h-3.5 w-3.5" /> {locale.startsWith("vi") ? "Xem chi tiết" : "View"}
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Right Col: Invited Queue */}
        <Card className="md:col-span-1">
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <UserCheck className="h-4 w-4 text-indigo-600" /> {locale.startsWith("vi") ? "Hàng chờ đã mời" : "Invited Queue"} ({invitedQueue.length})
            </CardTitle>
          </CardHeader>
          <CardContent>
            {invitedQueue.length === 0 ? (
              <p className="text-xs text-muted-foreground py-4">
                {locale.startsWith("vi") ? "Không có ứng viên ở trạng thái Đã mời chờ lên lịch." : "No candidates in INVITED state awaiting scheduling."}
              </p>
            ) : (
              <div className="space-y-3">
                {invitedQueue.map((inv) => (
                  <div key={inv.id} className="p-3 rounded-lg border bg-slate-50 space-y-2 text-xs">
                    <div className="flex items-center justify-between">
                      <strong className="font-medium text-sm text-foreground">{inv.candidateName}</strong>
                      <Badge variant="outline" className="text-[10px]">
                        {formatEnumLabel(inv.status, locale)}
                      </Badge>
                    </div>
                    <p className="text-muted-foreground truncate">{locale.startsWith("vi") ? "Tin: " : "Job: "}{inv.jobTitle}</p>
                    <div className="flex justify-end">
                      <Button size="sm" variant="outline" className="h-7 text-xs" nativeButton={false} render={<Link href={`/recruitment/interviews/${inv.id}`} />}>
                        {locale.startsWith("vi") ? "Lên lịch ứng viên" : "Schedule Candidate"}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  cancelInterviewAdmin,
  dialInterview,
  getInterviewAttempts,
  getInterviewDeliveryHistory,
  getInterviewRecordings,
  getInterviewResult,
  getInterviewSlotsAdmin,
  getInterviewTranscript,
  getRecruitmentInterview,
  recordingUrl,
  reinviteInterview,
  rescheduleInterviewAdmin,
  scheduleInterviewAdmin,
  type CallAttempt,
  type DeliveryHistoryItem,
  type InterviewRecording,
  type InterviewResult,
  type InterviewTranscript,
  type RecruitmentInterview,
  type InterviewSlot,
} from "@/lib/recruitment-admin-api";
import {
  ArrowLeft,
  Calendar,
  Clock,
  Download,
  Mic,
  PhoneCall,
  RefreshCw,
  XCircle,
  FileText,
  CheckCircle,
  AlertCircle,
  Play,
  Mail,
} from "lucide-react";

import { formatEnumLabel, formatTimezoneLabel } from "@/lib/recruitment-formatters";

export function InterviewDetailPage({ interviewId }: { interviewId: string }) {
  const t = useTranslations("Recruitment");
  const locale = useLocale();
  const format = useFormatter();
  const { request } = useApiClient();

  const [interview, setInterview] = useState<RecruitmentInterview | null>(null);
  const [attempts, setAttempts] = useState<CallAttempt[]>([]);
  const [deliveryHistory, setDeliveryHistory] = useState<DeliveryHistoryItem[]>([]);
  const [transcript, setTranscript] = useState<InterviewTranscript | null>(null);
  const [result, setResult] = useState<InterviewResult | null>(null);
  const [recordings, setRecordings] = useState<InterviewRecording[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [acting, setActing] = useState(false);

  // Schedule / Reschedule Dialog
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const [isReschedule, setIsReschedule] = useState(false);
  const [slots, setSlots] = useState<InterviewSlot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState("");
  const [manualTime, setManualTime] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const inv = await getRecruitmentInterview(request, interviewId);
      setInterview(inv);

      // Load sub-resources concurrently
      const [attRes, delRes, recRes] = await Promise.all([
        getInterviewAttempts(request, interviewId).catch(() => []),
        getInterviewDeliveryHistory(request, interviewId).catch(() => []),
        getInterviewRecordings(request, interviewId).catch(() => []),
      ]);
      setAttempts(attRes);
      setDeliveryHistory(delRes);
      setRecordings(recRes);

      if (inv.status === "COMPLETED" || inv.status === "IN_PROGRESS") {
        const [transRes, resRes] = await Promise.all([
          getInterviewTranscript(request, interviewId).catch(() => null),
          getInterviewResult(request, interviewId).catch(() => null),
        ]);
        setTranscript(transRes);
        setResult(resRes);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [interviewId, request, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const openScheduleModal = async (reschedule: boolean) => {
    setIsReschedule(reschedule);
    setError("");
    try {
      const res = await getInterviewSlotsAdmin(request, interviewId);
      setSlots(res.items);
      setScheduleModalOpen(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    }
  };

  const handleConfirmSchedule = async () => {
    const startAt = selectedSlot || (manualTime ? new Date(manualTime).toISOString() : "");
    if (!startAt) return;
    setActing(true);
    setError("");
    try {
      if (isReschedule) {
        await rescheduleInterviewAdmin(request, interviewId, startAt);
        setActionMessage("Interview rescheduled successfully.");
      } else {
        await scheduleInterviewAdmin(request, interviewId, startAt);
        setActionMessage("Interview scheduled successfully.");
      }
      setScheduleModalOpen(false);
      setSelectedSlot("");
      setManualTime("");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleCancel = async () => {
    if (!window.confirm("Cancel this scheduled interview? Application will return to UNDER_REVIEW.")) return;
    setActing(true);
    try {
      await cancelInterviewAdmin(request, interviewId);
      setActionMessage("Interview cancelled.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleReinvite = async () => {
    if (!window.confirm("Issue a fresh invitation token and reinvite candidate for this interview?")) return;
    setActing(true);
    try {
      await reinviteInterview(request, interviewId);
      setActionMessage("Interview reinvitation token generated and invitation sent.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleDial = async () => {
    if (!window.confirm("Trigger immediate manual outbound voice call dispatch? (Admin action)")) return;
    setActing(true);
    try {
      await dialInterview(request, interviewId);
      setActionMessage("Outbound call attempt initiated.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  if (loading) return <p className="p-6 text-sm text-muted-foreground">{t("loading")}</p>;
  if (!interview) return <p className="p-6 text-sm text-red-600">{error || t("loadError")}</p>;

  const canReinvite = ["FAILED", "NO_ANSWER", "DECLINED", "EXPIRED", "CANCELLED"].includes(interview.status);

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Top Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b pb-4">
        <div>
          <Button variant="ghost" size="sm" nativeButton={false} render={<Link href="/recruitment/interviews" />}>
            <ArrowLeft className="mr-1 h-4 w-4" /> Back to Interviews
          </Button>
          <div className="flex items-center gap-3 mt-2">
            <h2 className="text-2xl font-bold">{interview.candidateName}</h2>
            <Badge variant="outline" className="text-sm font-semibold">
              {formatEnumLabel(interview.status, locale)}
            </Badge>
            {interview.overallScore !== null && interview.overallScore !== undefined && (
              <Badge variant="secondary" className="text-sm font-bold bg-indigo-50 text-indigo-700 border-indigo-200">
                Score: {interview.overallScore}/100
              </Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Job: <span className="font-semibold text-foreground">{interview.jobTitle}</span> · Application ID:{" "}
            <Link href={`/recruitment/applications/${interview.applicationId}`} className="underline text-indigo-600">
              {interview.applicationId.slice(0, 8)}…
            </Link>
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex flex-wrap gap-2">
          {interview.status === "INVITED" && (
            <Button variant="default" size="sm" disabled={acting} onClick={() => void openScheduleModal(false)}>
              <Calendar className="mr-1 h-4 w-4" /> Schedule Slot
            </Button>
          )}

          {interview.status === "SCHEDULED" && (
            <>
              <Button variant="outline" size="sm" disabled={acting} onClick={() => void openScheduleModal(true)}>
                <RefreshCw className="mr-1 h-4 w-4" /> Reschedule
              </Button>
              <Button variant="outline" size="sm" disabled={acting} onClick={() => void handleDial()}>
                <PhoneCall className="mr-1 h-4 w-4" /> Manual Dial
              </Button>
              <Button variant="destructive" size="sm" disabled={acting} onClick={() => void handleCancel()}>
                <XCircle className="mr-1 h-4 w-4" /> Cancel
              </Button>
            </>
          )}

          {canReinvite && (
            <Button variant="default" size="sm" disabled={acting} onClick={() => void handleReinvite()}>
              <RefreshCw className="mr-1 h-4 w-4" /> Controlled Reinvite
            </Button>
          )}
        </div>
      </div>

      {error && <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {actionMessage && <p className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">{actionMessage}</p>}

      {/* Tabs */}
      <Tabs defaultValue="overview">
        <TabsList className="grid w-full grid-cols-6">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="attempts">Attempts ({attempts.length})</TabsTrigger>
          <TabsTrigger value="delivery">Delivery ({deliveryHistory.length})</TabsTrigger>
          <TabsTrigger value="transcript">Transcript</TabsTrigger>
          <TabsTrigger value="result">Result Report</TabsTrigger>
          <TabsTrigger value="recordings">Recordings ({recordings.length})</TabsTrigger>
        </TabsList>

        {/* Overview Tab */}
        <TabsContent value="overview" className="mt-4 space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Clock className="h-4 w-4 text-indigo-600" /> Schedule Information
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div>
                  <p className="text-xs text-muted-foreground">Scheduled Start Time</p>
                  <p className="font-medium">
                    {interview.scheduledStartAt ? format.dateTime(new Date(interview.scheduledStartAt), { dateStyle: "full", timeStyle: "short" }) : "Not scheduled"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Scheduled End Time</p>
                  <p className="font-medium">
                    {interview.scheduledEndAt ? format.dateTime(new Date(interview.scheduledEndAt), { timeStyle: "short" }) : "—"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Timezone</p>
                  <p className="font-medium">{formatTimezoneLabel(interview.schedulingTimezone)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Reschedule Count</p>
                  <p className="font-medium">{interview.rescheduleCount}</p>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Mic className="h-4 w-4 text-indigo-600" /> Call Execution
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div>
                  <p className="text-xs text-muted-foreground">Started At</p>
                  <p>{interview.startedAt ? format.dateTime(new Date(interview.startedAt), { dateStyle: "short", timeStyle: "medium" }) : "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Completed At</p>
                  <p>{interview.completedAt ? format.dateTime(new Date(interview.completedAt), { dateStyle: "short", timeStyle: "medium" }) : "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">English Band Evaluation</p>
                  <p className="font-semibold text-indigo-700">{interview.englishBand || "—"}</p>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* Call Attempts Tab */}
        <TabsContent value="attempts" className="mt-4 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Call Attempts History</CardTitle>
              <CardDescription>Twilio voice dispatches and state progressions for this interview.</CardDescription>
            </CardHeader>
            <CardContent>
              {attempts.length === 0 ? (
                <p className="text-sm text-muted-foreground">No call attempts logged yet.</p>
              ) : (
                <div className="space-y-3">
                  {attempts.map((att) => (
                    <div key={att.attemptNumber} className="rounded-lg border p-4 bg-slate-50/50 space-y-2 text-sm">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold">Attempt #{att.attemptNumber}</span>
                        <Badge variant="outline">{att.status}</Badge>
                      </div>
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs text-muted-foreground">
                        <div>Created: {format.dateTime(new Date(att.createdAt), { timeStyle: "short" })}</div>
                        <div>Answered: {att.answeredAt ? format.dateTime(new Date(att.answeredAt), { timeStyle: "short" }) : "—"}</div>
                        <div>Consented: {att.consentedAt ? "Yes" : "No"}</div>
                        <div>Terminal: {att.terminalAt ? format.dateTime(new Date(att.terminalAt), { timeStyle: "short" }) : "Active"}</div>
                      </div>
                      {att.failureCode && <p className="text-xs font-semibold text-red-700">Failure Reason: {att.failureCode}</p>}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Delivery History Tab */}
        <TabsContent value="delivery" className="mt-4 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Mail className="h-4 w-4 text-indigo-600" /> Email & Delivery History
              </CardTitle>
              <CardDescription>Audit of invitation, confirmation, reminder, and retry notifications sent to candidate.</CardDescription>
            </CardHeader>
            <CardContent>
              {deliveryHistory.length === 0 ? (
                <p className="text-sm text-muted-foreground">No delivery history records found.</p>
              ) : (
                <div className="space-y-3">
                  {deliveryHistory.map((item) => (
                    <div key={item.id} className="rounded-md border p-3 flex items-center justify-between text-sm">
                      <div>
                        <p className="font-semibold">{item.type.replaceAll("_", " ")}</p>
                        <p className="text-xs text-muted-foreground">Recipient: {item.recipient}</p>
                      </div>
                      <div className="text-right">
                        <Badge variant={item.status === "SENT" ? "default" : "outline"}>{item.status}</Badge>
                        <p className="text-xs text-muted-foreground mt-1">
                          {item.sentAt ? format.dateTime(new Date(item.sentAt), { dateStyle: "short", timeStyle: "short" }) : "Pending"}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Transcript Tab */}
        <TabsContent value="transcript" className="mt-4 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <FileText className="h-4 w-4 text-indigo-600" /> Interview Transcript
              </CardTitle>
              <CardDescription>Audited turn-by-turn conversation log between AI Intervewer and Candidate.</CardDescription>
            </CardHeader>
            <CardContent>
              {!transcript || transcript.turns.length === 0 ? (
                <p className="text-sm text-muted-foreground">No transcript turns available for this interview.</p>
              ) : (
                <div className="space-y-4">
                  {transcript.turns.map((turn) => (
                    <div
                      key={turn.turnId}
                      className={`p-3 rounded-lg border text-sm ${
                        turn.speaker === "AI" ? "bg-indigo-50/50 border-indigo-100 ml-4" : "bg-white border-slate-200 mr-4"
                      }`}
                    >
                      <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
                        <span className="font-semibold text-foreground">
                          #{turn.sequence} · {turn.speaker === "AI" ? "AI Interviewer" : "Candidate"}
                        </span>
                        <span>{turn.languageTag}</span>
                      </div>
                      <p className="text-slate-800 leading-relaxed">{turn.transcript}</p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Evaluation Result Tab */}
        <TabsContent value="result" className="mt-4 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <CheckCircle className="h-4 w-4 text-indigo-600" /> AI Evaluation & Scoring Report
              </CardTitle>
              <CardDescription>Section breakdown, competency scores, and advisory warnings.</CardDescription>
            </CardHeader>
            <CardContent>
              {!result ? (
                <p className="text-sm text-muted-foreground">No evaluation result compiled yet.</p>
              ) : (
                <div className="space-y-6">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 rounded-lg bg-slate-50 border">
                    <div>
                      <p className="text-xs text-muted-foreground">Overall Score</p>
                      <p className="text-2xl font-bold text-indigo-700">{result.overallScore ?? "N/A"}/100</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">English Proficiency</p>
                      <p className="text-lg font-semibold">{result.englishBand || "N/A"}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Terminal Kind</p>
                      <p className="text-sm font-medium">{result.terminalKind}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Delivery Status</p>
                      <p className="text-sm font-medium">{result.deliveryStatus}</p>
                    </div>
                  </div>

                  {result.englishWarning && (
                    <p className="rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800 flex items-center gap-2">
                      <AlertCircle className="h-4 w-4 shrink-0" />
                      {result.englishWarning}
                    </p>
                  )}

                  {/* Section & Question details */}
                  <div className="space-y-4">
                    <h4 className="font-semibold text-sm">Section Breakdown</h4>
                    {result.sections.map((sec) => (
                      <div key={sec.sectionId} className="rounded-lg border p-4 space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-sm">Kind: {sec.kind}</span>
                          <Badge variant="outline">{sec.status}</Badge>
                        </div>
                        <div className="space-y-2">
                          {sec.questions.map((q) => (
                            <div key={q.questionId} className="flex items-center justify-between text-xs bg-slate-50 p-2 rounded">
                              <span>Question ID: {q.questionId.slice(0, 8)}…</span>
                              <span className="font-bold text-indigo-700">Score: {q.score !== null ? `${q.score}/100` : "N/A"}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                  <p className="rounded-md bg-amber-50 p-3 text-xs text-amber-800 border border-amber-200">{t("advisory")}</p>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Recordings Tab */}
        <TabsContent value="recordings" className="mt-4 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Mic className="h-4 w-4 text-indigo-600" /> Audio Recordings
              </CardTitle>
              <CardDescription>Listen to or download interview call recordings.</CardDescription>
            </CardHeader>
            <CardContent>
              {recordings.length === 0 ? (
                <p className="text-sm text-muted-foreground">No audio recordings available for this interview.</p>
              ) : (
                <div className="space-y-4">
                  {recordings.map((rec) => (
                    <div key={rec.recordingId} className="rounded-lg border p-4 bg-slate-50 space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-sm">Recording ID: {rec.recordingId.slice(0, 8)}…</span>
                        <Badge variant="outline">{rec.state}</Badge>
                      </div>
                      {/* Audio Player */}
                      <audio controls className="w-full h-10">
                        <source src={recordingUrl(interview.id, rec.recordingId, false)} type={rec.contentType || "audio/mp3"} />
                        Your browser does not support HTML5 audio player.
                      </audio>
                      <div className="flex items-center justify-between text-xs text-muted-foreground">
                        <span>
                          Retained Until: {rec.retainedUntil ? format.dateTime(new Date(rec.retainedUntil), { dateStyle: "short" }) : "Indefinite"}
                        </span>
                        <Button
                          size="sm"
                          variant="outline"
                          nativeButton={false}
                          render={<a href={recordingUrl(interview.id, rec.recordingId, true)} download target="_blank" rel="noreferrer" />}
                        >
                          <Download className="mr-1 h-3.5 w-3.5" /> Download MP3
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Admin Schedule / Reschedule Dialog */}
      <Dialog open={scheduleModalOpen} onOpenChange={setScheduleModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{isReschedule ? "Reschedule Interview" : "Schedule Interview Slot"}</DialogTitle>
            <DialogDescription>Select an available slot according to tenant availability and notice policies.</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            {slots.length > 0 ? (
              <div className="space-y-2">
                <label className="text-sm font-medium">Available Timeslots</label>
                <select
                  className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm"
                  value={selectedSlot}
                  onChange={(e) => setSelectedSlot(e.target.value)}
                >
                  <option value="">Choose a slot...</option>
                  {slots.map((sl, idx) => (
                    <option key={idx} value={sl.startAt}>
                      {format.dateTime(new Date(sl.startAt), { dateStyle: "medium", timeStyle: "short" })} ({sl.schedulingTimezone})
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <p className="text-xs text-amber-700 bg-amber-50 p-2 rounded">No auto-generated slots found in the next 14 days.</p>
            )}

            <div className="space-y-2">
              <label className="text-sm font-medium">Or Custom Start Datetime</label>
              <Input type="datetime-local" value={manualTime} onChange={(e) => setManualTime(e.target.value)} />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setScheduleModalOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => void handleConfirmSchedule()} disabled={(!selectedSlot && !manualTime) || acting}>
              {acting ? "Saving..." : "Confirm Schedule"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

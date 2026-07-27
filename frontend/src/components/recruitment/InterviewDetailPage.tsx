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
  getDialEligibility,
  getInterviewAttempts,
  getInterviewDeliveryHistory,
  getRecordingBlob,
  getInterviewRecordings,
  getInterviewResult,
  getInterviewSlotsAdmin,
  getInterviewTranscript,
  getRecruitmentInterview,
  reinviteInterview,
  rescheduleInterviewAdmin,
  scheduleInterviewAdmin,
  type CallAttempt,
  type DialEligibility,
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
  Mail,
} from "lucide-react";

import { formatEnumLabel, formatTimezoneLabel } from "@/lib/recruitment-formatters";
import { callFailureText } from "@/lib/recruitment-call-failures";
import { useRecruitmentConfirmation } from "@/components/recruitment/useRecruitmentConfirmation";

const DEVELOPMENT_REDIAL_STATUSES = ["COMPLETED", "FAILED", "DECLINED", "NO_ANSWER", "CANCELLED", "EXPIRED"];
const RECORDING_PROCESSING_STATES = new Set(["START_PENDING", "RECORDING", "COPY_PENDING", "DELETE_PROVIDER_PENDING", "DELETE_PENDING"]);

export function InterviewDetailPage({ interviewId }: { interviewId: string }) {
  const t = useTranslations("Recruitment");
  const i = useTranslations("Recruitment.interviewPages");
  const locale = useLocale();
  const format = useFormatter();
  const { request } = useApiClient();
  const { confirm, confirmationDialog } = useRecruitmentConfirmation();

  const [interview, setInterview] = useState<RecruitmentInterview | null>(null);
  const [attempts, setAttempts] = useState<CallAttempt[]>([]);
  const [deliveryHistory, setDeliveryHistory] = useState<DeliveryHistoryItem[]>([]);
  const [transcript, setTranscript] = useState<InterviewTranscript | null>(null);
  const [result, setResult] = useState<InterviewResult | null>(null);
  const [recordings, setRecordings] = useState<InterviewRecording[]>([]);
  const [recordingPlaybackUrls, setRecordingPlaybackUrls] = useState<Record<string, string>>({});
  const [recordingPlaybackErrors, setRecordingPlaybackErrors] = useState<Set<string>>(new Set());
  const [downloadingRecordingId, setDownloadingRecordingId] = useState<string | null>(null);
  const [dialEligibility, setDialEligibility] = useState<DialEligibility | null>(null);
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
      const [attRes, delRes, recRes, dialRes] = await Promise.all([
        getInterviewAttempts(request, interviewId).catch(() => []),
        getInterviewDeliveryHistory(request, interviewId).catch(() => []),
        getInterviewRecordings(request, interviewId).catch(() => []),
        (inv.status === "SCHEDULED" || DEVELOPMENT_REDIAL_STATUSES.includes(inv.status))
          ? getDialEligibility(request, interviewId).catch(() => null)
          : Promise.resolve(null),
      ]);
      setAttempts(attRes);
      setDeliveryHistory(delRes);
      setRecordings(recRes);
      setDialEligibility(dialRes);

      if (inv.status === "COMPLETED" || inv.status === "IN_PROGRESS") {
        const [transRes, resRes] = await Promise.all([
          getInterviewTranscript(request, interviewId).catch(() => null),
          getInterviewResult(request, interviewId).catch(() => null),
        ]);
        setTranscript(transRes);
        setResult(resRes);
      } else {
        setTranscript(null);
        setResult(null);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [interviewId, request, t]);

  useEffect(() => {
    const timer=window.setTimeout(()=>void load(),0);
    return()=>window.clearTimeout(timer);
  }, [load]);

  const refreshRecordings = useCallback(async () => {
    setRecordings(await getInterviewRecordings(request, interviewId));
  }, [interviewId, request]);

  useEffect(() => {
    if(!recordings.some((recording)=>RECORDING_PROCESSING_STATES.has(recording.state)))return;
    const timer=window.setInterval(()=>void refreshRecordings().catch(()=>undefined),3000);
    return()=>window.clearInterval(timer);
  }, [recordings, refreshRecordings]);

  useEffect(() => {
    let cancelled=false;
    const objectUrls:string[]=[];
    const ready=recordings.filter((recording)=>recording.state==="READY");
    if(ready.length===0)return;
    void Promise.all(ready.map(async(recording)=>{
      try {
        const blob=await getRecordingBlob(request,interviewId,recording.recordingId);
        const url=URL.createObjectURL(blob);
        if(cancelled){URL.revokeObjectURL(url);return [recording.recordingId,null] as const;}
        objectUrls.push(url);
        return [recording.recordingId,url] as const;
      } catch {
        return [recording.recordingId,null] as const;
      }
    })).then((loaded)=>{
      if(cancelled)return;
      setRecordingPlaybackUrls(Object.fromEntries(loaded.filter((entry):entry is readonly [string,string]=>entry[1]!==null)));
      setRecordingPlaybackErrors(new Set(loaded.filter((entry)=>entry[1]===null).map((entry)=>entry[0])));
    });
    return()=>{cancelled=true;objectUrls.forEach((url)=>URL.revokeObjectURL(url));};
  }, [interviewId, recordings, request]);

  const downloadRecording = async (recording:InterviewRecording) => {
    setDownloadingRecordingId(recording.recordingId);setError("");
    try {
      const blob=await getRecordingBlob(request,interviewId,recording.recordingId,true);
      const url=URL.createObjectURL(blob);const anchor=document.createElement("a");
      anchor.href=url;anchor.download=`interview-recording-${recording.recordingId}.mp3`;
      document.body.appendChild(anchor);anchor.click();anchor.remove();URL.revokeObjectURL(url);
    } catch {setError(i("recordingDownloadFailed"));}
    finally {setDownloadingRecordingId(null);}
  };

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
        setActionMessage(i("rescheduledSuccess"));
      } else {
        await scheduleInterviewAdmin(request, interviewId, startAt);
        setActionMessage(i("scheduledSuccess"));
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
    if (!await confirm({ title: t("dialogs.cancelInterviewTitle"), description: t("dialogs.cancelInterview"), confirmLabel: t("actions.cancel"), destructive: true })) return;
    setActing(true);
    try {
      await cancelInterviewAdmin(request, interviewId);
      setActionMessage(i("cancelledSuccess"));
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleReinvite = async () => {
    if (!await confirm({ title: t("dialogs.reinviteInterviewTitle"), description: t("dialogs.reinviteInterview"), confirmLabel: t("actions.invite") })) return;
    setActing(true);
    try {
      await reinviteInterview(request, interviewId);
      setActionMessage(i("reinviteSuccess"));
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleDial = async () => {
    if (!dialEligibility?.allowed) return;
    const developmentRedial=interview ? DEVELOPMENT_REDIAL_STATUSES.includes(interview.status) : false;
    if (!await confirm({
      title: developmentRedial ? i("developmentRedialTitle") : t("dialogs.dialInterviewTitle"),
      description: developmentRedial ? i("developmentRedialDescription") : t("dialogs.dialInterview"),
      confirmLabel: developmentRedial ? i("developmentRedial") : t("dialogs.dialInterviewTitle"),
      destructive: developmentRedial,
    })) return;
    setActing(true);
    setError("");
    try {
      const response = await dialInterview(request, interviewId);
      await load();
      if (response.failureCode) {
        setActionMessage("");
        setError(callFailureText(response.failureCode, i, locale));
      } else {
        setActionMessage(i("dialSuccess"));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  if (loading) return <p className="p-6 text-sm text-muted-foreground">{t("loading")}</p>;
  if (!interview) return <p className="p-6 text-sm text-red-600">{error || t("loadError")}</p>;

  const developmentRedial = DEVELOPMENT_REDIAL_STATUSES.includes(interview.status) && dialEligibility?.allowed;
  const canReinvite = ["FAILED", "NO_ANSWER", "DECLINED", "EXPIRED", "CANCELLED"].includes(interview.status)
    && !developmentRedial;
  const hasEnglishSection = result?.sections.some((section) => section.kind === "ENGLISH_SCREEN") ?? false;
  const dialWindowMessage = dialEligibility && !dialEligibility.allowed
    ? dialEligibility.reason === "OUTSIDE_DIAL_WINDOW" && dialEligibility.windowOpensAt
      ? new Date(dialEligibility.serverTime) < new Date(dialEligibility.windowOpensAt)
        ? i("dialNotOpen", {
            time: format.dateTime(new Date(dialEligibility.windowOpensAt), { dateStyle: "medium", timeStyle: "short" }),
            scheduled: interview.scheduledStartAt ? format.dateTime(new Date(interview.scheduledStartAt), { dateStyle: "medium", timeStyle: "short" }) : "—",
          })
        : i("dialWindowExpired", { time: dialEligibility.windowClosesAt ? format.dateTime(new Date(dialEligibility.windowClosesAt), { dateStyle: "medium", timeStyle: "short" }) : "—" })
      : dialEligibility.reason ? callFailureText(dialEligibility.reason, i, locale) : i("dialUnavailable")
    : null;

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Top Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b pb-4">
        <div>
          <Button variant="ghost" size="sm" nativeButton={false} render={<Link href="/recruitment/interviews" />}>
            <ArrowLeft className="mr-1 h-4 w-4" /> {i("back")}
          </Button>
          <div className="flex items-center gap-3 mt-2">
            <h2 className="text-2xl font-bold">{interview.candidateName}</h2>
            <Badge variant="outline" className="text-sm font-semibold">
              {formatEnumLabel(interview.status, locale)}
            </Badge>
            {interview.overallScore !== null && interview.overallScore !== undefined && (
              <Badge variant="secondary" className="text-sm font-bold bg-indigo-50 text-indigo-700 border-indigo-200">
                {i("score")}: {interview.overallScore}/100
              </Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            {i("job")}: <span className="font-semibold text-foreground">{interview.jobTitle}</span> · {i("applicationId")}:{" "}
            <Link href={`/recruitment/applications/${interview.applicationId}`} className="underline text-indigo-600">
              {interview.applicationId.slice(0, 8)}…
            </Link>
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex flex-wrap gap-2">
          {interview.status === "INVITED" && (
            <Button variant="default" size="sm" disabled={acting} onClick={() => void openScheduleModal(false)}>
              <Calendar className="mr-1 h-4 w-4" /> {i("scheduleSlot")}
            </Button>
          )}

          {interview.status === "SCHEDULED" && (
            <>
              <Button variant="outline" size="sm" disabled={acting} onClick={() => void openScheduleModal(true)}>
                <RefreshCw className="mr-1 h-4 w-4" /> {i("reschedule")}
              </Button>
              {dialEligibility && <Button variant="outline" size="sm" disabled={acting || !dialEligibility.allowed} onClick={() => void handleDial()}>
                <PhoneCall className="mr-1 h-4 w-4" /> {i("manualDial")}
              </Button>}
              <Button variant="destructive" size="sm" disabled={acting} onClick={() => void handleCancel()}>
                <XCircle className="mr-1 h-4 w-4" /> {i("cancel")}
              </Button>
            </>
          )}

          {developmentRedial && (
            <Button variant="outline" size="sm" disabled={acting} onClick={() => void handleDial()}>
              <PhoneCall className="mr-1 h-4 w-4" /> {i("developmentRedial")}
            </Button>
          )}

          {canReinvite && (
            <Button variant="default" size="sm" disabled={acting} onClick={() => void handleReinvite()}>
              <RefreshCw className="mr-1 h-4 w-4" /> {i("reinvite")}
            </Button>
          )}
        </div>
      </div>

      {error && <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {actionMessage && <p className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">{actionMessage}</p>}
      {dialWindowMessage && <p className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"><Clock className="mt-0.5 size-4 shrink-0" aria-hidden="true"/><span>{dialWindowMessage}</span></p>}

      {/* Tabs */}
      <Tabs defaultValue="overview">
        <TabsList className="grid w-full grid-cols-6">
          <TabsTrigger value="overview">{i("overview")}</TabsTrigger>
          <TabsTrigger value="attempts">{i("attempts", { count: attempts.length })}</TabsTrigger>
          <TabsTrigger value="delivery">{i("delivery", { count: deliveryHistory.length })}</TabsTrigger>
          <TabsTrigger value="transcript">{i("transcript")}</TabsTrigger>
          <TabsTrigger value="result">{i("resultReport")}</TabsTrigger>
          <TabsTrigger value="recordings">{i("recordings", { count: recordings.length })}</TabsTrigger>
        </TabsList>

        {/* Overview Tab */}
        <TabsContent value="overview" className="mt-4 space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Clock className="h-4 w-4 text-indigo-600" /> {i("scheduleInformation")}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div>
                  <p className="text-xs text-muted-foreground">{i("scheduledStart")}</p>
                  <p className="font-medium">
                    {interview.scheduledStartAt ? format.dateTime(new Date(interview.scheduledStartAt), { dateStyle: "full", timeStyle: "short" }) : i("notScheduled")}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{i("scheduledEnd")}</p>
                  <p className="font-medium">
                    {interview.scheduledEndAt ? format.dateTime(new Date(interview.scheduledEndAt), { timeStyle: "short" }) : "—"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{i("timezone")}</p>
                  <p className="font-medium">{formatTimezoneLabel(interview.schedulingTimezone)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{i("rescheduleCount")}</p>
                  <p className="font-medium">{interview.rescheduleCount}</p>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Mic className="h-4 w-4 text-indigo-600" /> {i("callExecution")}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div>
                  <p className="text-xs text-muted-foreground">{i("startedAt")}</p>
                  <p>{interview.startedAt ? format.dateTime(new Date(interview.startedAt), { dateStyle: "short", timeStyle: "medium" }) : "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{i("completedAt")}</p>
                  <p>{interview.completedAt ? format.dateTime(new Date(interview.completedAt), { dateStyle: "short", timeStyle: "medium" }) : "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{i("englishBand")}</p>
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
              <CardTitle className="text-base">{i("attemptHistory")}</CardTitle>
              <CardDescription>{i("attemptDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              {attempts.length === 0 ? (
                <p className="text-sm text-muted-foreground">{i("noAttempts")}</p>
              ) : (
                <div className="space-y-3">
                  {attempts.map((att) => (
                    <div key={att.attemptNumber} className="rounded-lg border p-4 bg-slate-50/50 space-y-2 text-sm">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold">{i("attemptNumber", { number: att.attemptNumber })}</span>
                        <Badge variant="outline">{formatEnumLabel(att.status, locale)}</Badge>
                      </div>
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs text-muted-foreground">
                        <div>{i("created")}: {format.dateTime(new Date(att.createdAt), { timeStyle: "short" })}</div>
                        <div>{i("answered")}: {att.answeredAt ? format.dateTime(new Date(att.answeredAt), { timeStyle: "short" }) : "—"}</div>
                        <div>{i("consented")}: {att.consentedAt ? i("yes") : i("no")}</div>
                        <div>{i("terminal")}: {att.terminalAt ? format.dateTime(new Date(att.terminalAt), { timeStyle: "short" }) : i("active")}</div>
                      </div>
                      {att.failureCode && <p className="text-xs font-semibold text-red-700">{i("failureReason")}: {callFailureText(att.failureCode,i,locale)}</p>}
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
                <Mail className="h-4 w-4 text-indigo-600" /> {i("deliveryHistory")}
              </CardTitle>
              <CardDescription>{i("deliveryDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              {deliveryHistory.length === 0 ? (
                <p className="text-sm text-muted-foreground">{i("noDelivery")}</p>
              ) : (
                <div className="space-y-3">
                  {deliveryHistory.map((item) => (
                    <div key={item.id} className="rounded-md border p-3 flex items-center justify-between text-sm">
                      <div>
                        <p className="font-semibold">{formatEnumLabel(item.type, locale)}</p>
                        <p className="text-xs text-muted-foreground">{i("recipient")}: {item.recipient}</p>
                      </div>
                      <div className="text-right">
                        <Badge variant={item.status === "SENT" ? "default" : "outline"}>{formatEnumLabel(item.status, locale)}</Badge>
                        <p className="text-xs text-muted-foreground mt-1">
                          {item.sentAt ? format.dateTime(new Date(item.sentAt), { dateStyle: "short", timeStyle: "short" }) : i("pending")}
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
                <FileText className="h-4 w-4 text-indigo-600" /> {i("interviewTranscript")}
              </CardTitle>
              <CardDescription>{i("transcriptDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              {!transcript || transcript.turns.length === 0 ? (
                <p className="text-sm text-muted-foreground">{i("noTranscript")}</p>
              ) : (
                <div className="space-y-4">
                  {transcript.turns.map((turn) => (
                    <div
                      key={turn.turnId}
                      className={`p-3 rounded-lg border text-sm ${
                        turn.speaker === "CANDIDATE" ? "bg-white border-slate-200 mr-4" : "bg-indigo-50/50 border-indigo-100 ml-4"
                      }`}
                    >
                      <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
                        <span className="font-semibold text-foreground">
                          #{turn.sequence} · {turn.speaker === "CANDIDATE" ? i("candidate") : i("aiInterviewer")}
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
                <CheckCircle className="h-4 w-4 text-indigo-600" /> {i("evaluationReport")}
              </CardTitle>
              <CardDescription>{i("evaluationDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              {!result ? (
                <p className="text-sm text-muted-foreground">{i("noEvaluation")}</p>
              ) : (
                <div className="space-y-6">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 rounded-lg bg-slate-50 border">
                    <div>
                      <p className="text-xs text-muted-foreground">{i("overallScore")}</p>
                      <p className="text-2xl font-bold text-indigo-700">
                        {result.overallScore !== null ? `${result.overallScore}/100` : i("notScored")}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">{i("englishProficiency")}</p>
                      <p className="text-lg font-semibold">
                        {hasEnglishSection ? result.englishBand || i("notScored") : i("notAssessed")}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">{i("terminalKind")}</p>
                      <p className="text-sm font-medium">{formatEnumLabel(result.terminalKind, locale)}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">{i("deliveryStatus")}</p>
                      <p className="text-sm font-medium">{formatEnumLabel(result.deliveryStatus, locale)}</p>
                    </div>
                  </div>

                  {result.failureCode && (
                    <p className="rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-800 flex items-start gap-2">
                      <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                      <span><strong>{i("failureReason")}:</strong> {callFailureText(result.failureCode, i, locale)}</span>
                    </p>
                  )}

                  {hasEnglishSection && result.englishWarning && (
                    <p className="rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800 flex items-center gap-2">
                      <AlertCircle className="h-4 w-4 shrink-0" />
                      {result.englishWarning}
                    </p>
                  )}

                  {/* Section & Question details */}
                  <div className="space-y-4">
                    <h4 className="font-semibold text-sm">{i("sectionBreakdown")}</h4>
                    {result.sections.map((sec) => (
                      <div key={sec.sectionId} className="rounded-lg border p-4 space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-sm">{i("kind")}: {formatEnumLabel(sec.kind, locale)}</span>
                          <Badge variant="outline">{formatEnumLabel(sec.status, locale)}</Badge>
                        </div>
                        <div className="space-y-2">
                          {sec.questions.map((q) => (
                            <div key={q.questionId} className="flex items-center justify-between text-xs bg-slate-50 p-2 rounded">
                              <span>{i("questionId")}: {q.questionId.slice(0, 8)}…</span>
                              <span className="font-bold text-indigo-700">
                                {i("score")}: {q.score !== null ? `${q.score}/5` : formatEnumLabel(q.status, locale)}
                              </span>
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
                <Mic className="h-4 w-4 text-indigo-600" /> {i("audioRecordings")}
              </CardTitle>
              <CardDescription>{i("recordingsDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              {recordings.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {interview.recordingEnabled ? i("noRecordings") : i("recordingDisabled")}
                </p>
              ) : (
                <div className="space-y-4">
                  {recordings.map((rec) => (
                    <div key={rec.recordingId} className="rounded-lg border p-4 bg-slate-50 space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-sm">{i("recordingId")}: {rec.recordingId.slice(0, 8)}…</span>
                        <Badge variant="outline">{formatEnumLabel(rec.state, locale)}</Badge>
                      </div>
                      {rec.state === "READY" ? (
                        recordingPlaybackUrls[rec.recordingId] ? (
                          <audio controls className="w-full h-10" src={recordingPlaybackUrls[rec.recordingId]}>
                            {i("audioUnsupported")}
                          </audio>
                        ) : (
                          <p className="text-sm text-muted-foreground">
                            {recordingPlaybackErrors.has(rec.recordingId)?i("recordingLoadFailed"):i("recordingLoading")}
                          </p>
                        )
                      ) : (
                        <p className="text-sm text-muted-foreground">{i("recordingProcessing")}</p>
                      )}
                      <div className="flex items-center justify-between text-xs text-muted-foreground">
                        <span>
                          {i("retainedUntil")}: {rec.retainedUntil ? format.dateTime(new Date(rec.retainedUntil), { dateStyle: "short" }) : i("indefinite")}
                        </span>
                        {rec.state === "READY" && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={downloadingRecordingId===rec.recordingId}
                            onClick={()=>void downloadRecording(rec)}
                          >
                            <Download className="mr-1 h-3.5 w-3.5" /> {i("downloadMp3")}
                          </Button>
                        )}
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
            <DialogTitle>{isReschedule ? i("rescheduleInterview") : i("scheduleInterview")}</DialogTitle>
            <DialogDescription>{i("scheduleDescription")}</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            {slots.length > 0 ? (
              <div className="space-y-2">
                <label className="text-sm font-medium">{i("availableSlots")}</label>
                <select
                  className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm"
                  value={selectedSlot}
                  onChange={(e) => setSelectedSlot(e.target.value)}
                >
                  <option value="">{i("chooseSlot")}</option>
                  {slots.map((sl, idx) => (
                    <option key={idx} value={sl.startAt}>
                      {format.dateTime(new Date(sl.startAt), { dateStyle: "medium", timeStyle: "short" })} ({sl.schedulingTimezone})
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <p className="text-xs text-amber-700 bg-amber-50 p-2 rounded">{i("noSlots")}</p>
            )}

            <div className="space-y-2">
              <label className="text-sm font-medium">{i("customStart")}</label>
              <Input type="datetime-local" value={manualTime} onChange={(e) => setManualTime(e.target.value)} />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setScheduleModalOpen(false)}>
              {i("cancel")}
            </Button>
            <Button onClick={() => void handleConfirmSchedule()} disabled={(!selectedSlot && !manualTime) || acting}>
              {acting ? i("saving") : i("confirmSchedule")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {confirmationDialog}
    </div>
  );
}

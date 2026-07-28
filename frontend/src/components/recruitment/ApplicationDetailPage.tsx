"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  getApplicationDetail,
  getCvAnalysis,
  refreshCvAnalysis,
  cvUrl,
  deleteRecruitmentCv,
  inviteApplication,
  sendApplicationCompletionLink,
  transitionApplication,
  type ApplicationDetail,
  type CvAnalysisResponse,
} from "@/lib/recruitment-admin-api";
import { ArrowLeft, Download, FileText, RefreshCw, Send, Trash2, User, CheckCircle, Clock, Sparkles } from "lucide-react";

import { formatEnumLabel } from "@/lib/recruitment-formatters";
import { useRecruitmentConfirmation } from "@/components/recruitment/useRecruitmentConfirmation";

export function cvAnalysisStateKey({cvPresent,applicationStatus,analysisStatus,mode}:{cvPresent:boolean;applicationStatus:string;analysisStatus:string;mode?:string|null}) {
  if(!cvPresent)return "aiUnavailable";
  if(applicationStatus==="SUBMITTED_UNVERIFIED")return "cvAwaitingVerification";
  if(mode==="OFF")return "cvDisabled";
  if(analysisStatus==="PENDING")return "aiPending";
  if(analysisStatus==="FAILED")return "cvFailed";
  if(analysisStatus==="SKIPPED_QUOTA")return "cvQuota";
  if(analysisStatus==="CANCELLED")return "cvCancelled";
  if(analysisStatus==="COMPLETED")return "cvCompletedEmpty";
  return "cvNotRequested";
}

export function ApplicationDetailPage({ applicationId }: { applicationId: string }) {
  const t = useTranslations("Recruitment");
  const a = useTranslations("Recruitment.applicationPages");
  const locale = useLocale();
  const format = useFormatter();
  const { request } = useApiClient();
  const router = useRouter();
  const { confirm, confirmationDialog } = useRecruitmentConfirmation();

  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [cvAnalysis, setCvAnalysis] = useState<CvAnalysisResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionSuccess, setActionSuccess] = useState("");
  const [cooldown, setCooldown] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await getApplicationDetail(request, applicationId);
      setDetail(res);
      if (res.application.cvPresent) {
        try {
          const cvRes = await getCvAnalysis(request, applicationId);
          setCvAnalysis(cvRes);
        } catch {
          // CV Analysis might be pending or not generated
        }
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [applicationId, request, t]);

  useEffect(() => {
    // Client-side route data is loaded when the application identity changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  useEffect(() => {
    if(cvAnalysis?.status!=="PENDING"&&cvAnalysis?.refreshStatus!=="PENDING")return;
    const timer=window.setInterval(()=>{void getCvAnalysis(request,applicationId).then(setCvAnalysis).catch(()=>undefined);},2000);
    return ()=>window.clearInterval(timer);
  },[applicationId,cvAnalysis?.refreshStatus,cvAnalysis?.status,request]);

  const handleTransition = async (targetStatus: string) => {
    setActing(true);
    setError("");
    setActionSuccess("");
    try {
      await transitionApplication(request, applicationId, targetStatus);
      setActionSuccess(a("statusUpdated", { status: formatEnumLabel(targetStatus, locale) }));
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleInvite = async () => {
    setActing(true);
    setError("");
    setActionSuccess("");
    try {
      const interview = await inviteApplication(request, applicationId);
      setActionSuccess(a("invitationIssued"));
      router.push(`/recruitment/interviews/${interview.id}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleResendCompletionLink = async () => {
    if (cooldown) return;
    setActing(true);
    setError("");
    try {
      await sendApplicationCompletionLink(request, applicationId);
      setCooldown(true);
      setTimeout(() => setCooldown(false), 60000);
      setActionSuccess(a("completionSent"));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleDeleteCv = async () => {
    if (!await confirm({ title: t("dialogs.deleteCvTitle"), description: t("dialogs.deleteCv"), confirmLabel: t("forms.delete"), destructive: true })) return;
    setActing(true);
    try {
      await deleteRecruitmentCv(request, applicationId);
      setActionSuccess(a("cvDeleted"));
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleRefreshAnalysis = async () => {
    if(!cvAnalysis?.refreshAvailable||cvAnalysis.refreshStatus==="PENDING"||acting)return;
    if(!await confirm({title:a("refreshTitle"),description:a("refreshConfirmation"),confirmLabel:a("refreshAction")}))return;
    setActing(true);setError("");setActionSuccess("");
    try{
      const refreshed=await refreshCvAnalysis(request,applicationId,crypto.randomUUID());
      setCvAnalysis(refreshed);setActionSuccess(a("refreshRequested"));
    }catch(cause){setError(cause instanceof Error?cause.message:t("loadError"));}
    finally{setActing(false);}
  };

  if (loading) return <p className="p-6 text-sm text-muted-foreground">{t("loading")}</p>;
  if (!detail) return <p className="p-6 text-sm text-red-600">{error || t("loadError")}</p>;

  const { application: app, candidate, screeningQuestions, screeningAnswers } = detail;
  const answerMap = new Map(screeningAnswers.map((a) => [a.questionId, a.optionId]));
  const cvAnalysisStatus=cvAnalysis?.status??app.cvAnalysisStatus;
  const cvAnalysisHasContent=Boolean(cvAnalysis?.summary||cvAnalysis?.skills?.length||cvAnalysis?.personalizedQuestions?.length||cvAnalysis?.evidence?.length||cvAnalysis?.fitScorePercent!==null&&cvAnalysis?.fitScorePercent!==undefined);
  const evidenceById=new Map((cvAnalysis?.evidence??[]).map(item=>[item.anchorId,item]));

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b pb-4">
        <div>
          <Button variant="ghost" size="sm" nativeButton={false} render={<Link href="/recruitment/applications" />}>
            <ArrowLeft className="mr-1 h-4 w-4" /> {a("back")}
          </Button>
          <div className="flex items-center gap-3 mt-2">
            <h2 className="text-2xl font-bold">{candidate.fullName}</h2>
            <Badge variant="outline" className="text-sm font-semibold">
              {formatEnumLabel(app.status, locale)}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            {a("applyingFor", { job: app.jobTitle })} · {candidate.email}
          </p>
        </div>

        {/* Header Action Buttons */}
        <div className="flex flex-wrap gap-2">
          {app.status === "AWAITING_CANDIDATE" && (
            <Button variant="outline" disabled={cooldown || acting} onClick={() => void handleResendCompletionLink()}>
              <Send className="mr-1 h-4 w-4" />
              {cooldown ? a("resent") : a("sendCompletionLink")}
            </Button>
          )}

          {["SUBMITTED", "UNDER_REVIEW"].includes(app.status) && (
            <Button variant="default" disabled={acting} onClick={() => void handleInvite()}>
              <CheckCircle className="mr-1 h-4 w-4" />
              {t("actions.invite")}
            </Button>
          )}

          {app.status !== "SHORTLISTED" && app.status !== "REJECTED" && app.status !== "WITHDRAWN" && (
            <>
              {app.status !== "UNDER_REVIEW" && (
                <Button variant="outline" size="sm" disabled={acting} onClick={() => void handleTransition("UNDER_REVIEW")}>
                  {a("underReview")}
                </Button>
              )}
              <Button variant="outline" size="sm" disabled={acting} onClick={() => void handleTransition("SHORTLISTED")}>
                {a("shortlist")}
              </Button>
              <Button variant="destructive" size="sm" disabled={acting} onClick={() => void handleTransition("REJECTED")}>
                {a("reject")}
              </Button>
            </>
          )}
        </div>
      </div>

      {error && <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {actionSuccess && <p className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">{actionSuccess}</p>}

      {/* Grid Content */}
      <div className="grid gap-6 md:grid-cols-3">
        {/* Left Column: Candidate & Application Details */}
        <div className="md:col-span-1 space-y-6">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <User className="h-4 w-4 text-indigo-600" /> {a("candidateProfile")}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div>
                <p className="text-xs text-muted-foreground">{a("fullName")}</p>
                <p className="font-medium">{candidate.fullName}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">{a("email")}</p>
                <p className="font-medium">{candidate.email}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">{a("phone")}</p>
                <p className="font-medium">{candidate.phone || "—"}</p>
              </div>
              {candidate.notes && (
                <div>
                  <p className="text-xs text-muted-foreground">{a("notes")}</p>
                  <p className="text-xs bg-slate-50 p-2 rounded border">{candidate.notes}</p>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <Clock className="h-4 w-4 text-indigo-600" /> {a("lifecycle")}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div>
                <p className="text-xs text-muted-foreground">{a("applicationStatus")}</p>
                <p className="font-semibold">{formatEnumLabel(app.status, locale)}</p>
              </div>
              {app.status === "SUBMITTED_UNVERIFIED" && <p className="rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">{a("verificationPending")}</p>}
              <div>
                <p className="text-xs text-muted-foreground">{a("submittedAt")}</p>
                <p>{app.submittedAt ? format.dateTime(new Date(app.submittedAt), { dateStyle: "long", timeStyle: "short" }) : a("notSubmitted")}</p>
              </div>
              {app.verifiedAt && (
                <div>
                  <p className="text-xs text-muted-foreground">{a("verifiedAt")}</p>
                  <p>{format.dateTime(new Date(app.verifiedAt), { dateStyle: "medium", timeStyle: "short" })}</p>
                </div>
              )}
              {app.withdrawnAt && (
                <div>
                  <p className="text-xs text-muted-foreground">{a("withdrawnAt")}</p>
                  <p className="text-red-600">{format.dateTime(new Date(app.withdrawnAt), { dateStyle: "medium", timeStyle: "short" })}</p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* CV Attachment Card */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <FileText className="h-4 w-4 text-indigo-600" /> {a("cvTitle")}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              {app.cvPresent ? (
                <>
                  <p className="text-xs text-green-700 bg-green-50 p-2 rounded border border-green-200">
                    {a("cvAvailable")}
                  </p>
                  <div className="flex flex-col gap-2">
                    <Button variant="outline" size="sm" nativeButton={false} render={<a href={cvUrl(app.id)} target="_blank" rel="noreferrer" />}>
                      <Download className="mr-2 h-4 w-4" /> {a("downloadCv")}
                    </Button>
                    <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50" onClick={() => void handleDeleteCv()}>
                      <Trash2 className="mr-2 h-4 w-4" /> {a("deleteCvPrivacy")}
                    </Button>
                  </div>
                </>
              ) : (
                <p className="text-xs text-muted-foreground">{a("noCv")}</p>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Screening Answers & CV AI Evidence */}
        <div className="md:col-span-2 space-y-6">
          <Tabs defaultValue="screening">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="screening">{a("screeningAnswers", { count: screeningQuestions.length })}</TabsTrigger>
              <TabsTrigger value="ai">
                <Sparkles className="mr-1 h-3.5 w-3.5" /> {a("cvAiReview")}
              </TabsTrigger>
            </TabsList>

            <TabsContent value="screening" className="mt-4 space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">{a("screeningEvidence")}</CardTitle>
                  <CardDescription>{a("screeningDescription")}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  {screeningQuestions.length === 0 && <p className="text-sm text-muted-foreground">{a("noScreening")}</p>}
                  {screeningQuestions.map((q, idx) => {
                    const chosenOptionId = answerMap.get(q.questionId);
                    const chosenOption = q.options.find((opt) => opt.optionId === chosenOptionId);
                    return (
                      <div key={q.questionId} className="rounded-lg border p-4 bg-slate-50/50 space-y-2">
                        <p className="text-sm font-semibold text-foreground">
                          {idx + 1}. {q.prompt}
                        </p>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground">{a("candidateAnswer")}:</span>
                          <span className="font-medium text-sm text-indigo-700 bg-indigo-50 px-2 py-0.5 rounded border border-indigo-200">
                            {chosenOption ? chosenOption.label : a("notAnswered")}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                  <p className="rounded-md bg-amber-50 p-3 text-xs text-amber-800 border border-amber-200">{t("advisory")}</p>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="ai" className="mt-4 space-y-4">
              <Card>
                <CardHeader>
                  <div className="flex flex-wrap items-center justify-between gap-2"><CardTitle className="text-base flex items-center gap-2"><Sparkles className="h-4 w-4 text-indigo-600" /> {a("aiTitle")}</CardTitle><div className="flex items-center gap-2"><Badge variant="outline">{formatEnumLabel(cvAnalysisStatus,locale)}</Badge>{cvAnalysis?.analysisRevision&&<Badge variant="secondary">{a("revision",{revision:cvAnalysis.analysisRevision})}</Badge>}</div></div>
                  <CardDescription>{a("aiDescription")}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  {!cvAnalysis || !cvAnalysisHasContent ? (
                    <div className="space-y-3"><p className="text-sm text-muted-foreground">{a(cvAnalysisStateKey({cvPresent:app.cvPresent,applicationStatus:app.status,analysisStatus:cvAnalysisStatus,mode:cvAnalysis?.mode}))}</p>{cvAnalysis&&<dl className="grid gap-3 rounded-md border bg-muted/20 p-3 text-xs sm:grid-cols-2"><div><dt className="text-muted-foreground">{a("cvAnalysisStatus")}</dt><dd className="font-medium">{formatEnumLabel(cvAnalysis.status,locale)}</dd></div><div><dt className="text-muted-foreground">{a("cvMode")}</dt><dd className="font-medium">{formatEnumLabel(cvAnalysis.mode,locale)}</dd></div>{cvAnalysis.failureCode&&<div className="sm:col-span-2"><dt className="text-muted-foreground">{a("failureCode")}</dt><dd className="font-mono">{cvAnalysis.failureCode}</dd></div>}</dl>}{cvAnalysis?.refreshAvailable&&<div className="flex items-center justify-between gap-3 border-t pt-3"><p className="text-xs text-muted-foreground">{a("refreshQuotaNotice")}</p><Button variant="outline" size="sm" disabled={acting} onClick={()=>void handleRefreshAnalysis()}><RefreshCw className="mr-1 h-3.5 w-3.5" />{a("refreshAction")}</Button></div>}</div>
                  ) : (
                    <>
                      {cvAnalysis.fitScorePercent!==null&&cvAnalysis.fitScorePercent!==undefined&&(
                        <div className="rounded-lg border border-indigo-200 bg-indigo-50/50 p-4">
                          <div className="flex flex-wrap items-end justify-between gap-3"><div><p className="text-xs font-medium uppercase tracking-wide text-indigo-700">{a("jobFitScore")}</p><p className="text-4xl font-bold text-indigo-950">{cvAnalysis.fitScorePercent}%</p></div>{cvAnalysis.fitConfidence&&<div className="text-right"><p className="text-xs text-muted-foreground">{a("confidence")}</p><Badge>{a(`confidence${cvAnalysis.fitConfidence}` as "confidenceLOW"|"confidenceMEDIUM"|"confidenceHIGH")}</Badge></div>}</div>
                          {cvAnalysis.fitExplanation&&<p className="mt-3 text-sm text-slate-700">{cvAnalysis.fitExplanation}</p>}
                          <p className="mt-3 rounded bg-white/80 p-2 text-xs text-amber-900">{a("fitAdvisory")}</p>
                        </div>
                      )}

                      {([...[cvAnalysis.strengths??[]].map(item=>({item,kind:"strength" as const})),...[cvAnalysis.gaps??[]].map(item=>({item,kind:"gap" as const}))].length>0)&&(
                        <div className="grid gap-4 sm:grid-cols-2">
                          {(["strength","gap"] as const).map(kind=><div key={kind}><h4 className="mb-2 text-sm font-semibold">{a(kind==="strength"?"strengths":"gaps")}</h4><div className="space-y-2">{(kind==="strength"?cvAnalysis.strengths??[]:cvAnalysis.gaps??[]).map((finding,idx)=><div key={`${kind}-${idx}`} className="rounded-md border p-3 text-sm"><div className="flex justify-between gap-2"><Badge variant={kind==="strength"?"secondary":"outline"}>{finding.matchPercent}%</Badge><span className="text-xs text-muted-foreground">{a("weight",{weight:finding.weightPercent})}</span></div><p className="mt-2">{finding.explanation}</p><div className="mt-2 rounded bg-slate-50 p-2 text-xs"><span className="font-medium">{a("jobEvidence")}:</span> “{finding.jobExcerpt}”</div><div className="mt-2 text-xs text-muted-foreground"><span className="font-medium">{a("cvEvidence")}:</span> {finding.evidenceStatus==="NOT_EVIDENCED"?a("notEvidenced"):(finding.cvEvidenceAnchorIds.map(id=>evidenceById.get(id)?.excerpt).filter(Boolean).join(" · ")||a("evidenceUnavailable"))}</div></div>)}</div></div>)}
                        </div>
                      )}
                      {/* Summary */}
                      {cvAnalysis.summary && (
                        <div className="rounded-md bg-slate-50 p-4 border">
                          <h4 className="font-semibold text-sm mb-1">{a("executiveSummary")}</h4>
                          <p className="text-sm text-slate-700 leading-relaxed">{cvAnalysis.summary}</p>
                        </div>
                      )}

                      {/* Skills */}
                      {cvAnalysis.skills && cvAnalysis.skills.length > 0 && (
                        <div>
                          <h4 className="font-semibold text-sm mb-2">{a("skills")}</h4>
                          <div className="flex flex-wrap gap-1.5">
                            {cvAnalysis.skills.map((skill, idx) => (
                              <Badge key={idx} variant="secondary">
                                {skill.name}
                              </Badge>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Personalized Questions */}
                      {cvAnalysis.personalizedQuestions && cvAnalysis.personalizedQuestions.length > 0 && (
                        <div>
                          <h4 className="font-semibold text-sm mb-2">{a("personalizedQuestions")}</h4>
                          <div className="space-y-2">
                            {cvAnalysis.personalizedQuestions.map((pq, idx) => (
                              <div key={idx} className="rounded-md border p-3 bg-indigo-50/40 text-sm">
                                <p className="font-medium text-foreground">{pq.prompt}</p>
                                <div className="flex justify-between text-xs text-muted-foreground mt-1">
                                  <span>{a("competency")}: {pq.competency}</span>
                                  <span>{a("rubric")}: {pq.rubric}</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Evidence Excerpts */}
                      {cvAnalysis.evidence && cvAnalysis.evidence.length > 0 && (
                        <div>
                          <h4 className="font-semibold text-sm mb-2">{a("verifiedExcerpts")}</h4>
                          <div className="space-y-2">
                            {cvAnalysis.evidence.map((ev, idx) => (
                              <div key={idx} className="rounded border p-2 text-xs bg-slate-50 italic">
                                “{ev.excerpt}” — <span className="text-muted-foreground font-normal">{ev.sourceLocation}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                      {cvAnalysis.refreshStatus==="FAILED"&&<p className="rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-800">{a("refreshFailed")}{cvAnalysis.refreshFailureCode?` (${cvAnalysis.refreshFailureCode})`:""}</p>}
                      {cvAnalysis.refreshStatus==="QUOTA_EXHAUSTED"&&<p className="rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">{a("refreshQuota")}</p>}
                      {cvAnalysis.refreshStatus==="PENDING"&&<p className="text-xs text-muted-foreground">{a("refreshPending")}</p>}
                      <div className="flex items-center justify-between gap-3 border-t pt-3"><p className="text-xs text-muted-foreground">{a("refreshQuotaNotice")}</p><Button variant="outline" size="sm" disabled={!cvAnalysis.refreshAvailable||cvAnalysis.refreshStatus==="PENDING"||acting} onClick={()=>void handleRefreshAnalysis()}><RefreshCw className={`mr-1 h-3.5 w-3.5 ${cvAnalysis.refreshStatus==="PENDING"?"animate-spin":""}`} />{a("refreshAction")}</Button></div>
                    </>
                  )}
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>
      </div>
      {confirmationDialog}
    </div>
  );
}

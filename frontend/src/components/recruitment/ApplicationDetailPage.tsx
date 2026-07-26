"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  getApplicationDetail,
  getCvAnalysis,
  cvUrl,
  deleteRecruitmentCv,
  inviteApplication,
  sendApplicationCompletionLink,
  transitionApplication,
  deleteRecruitmentApplication,
  type ApplicationDetail,
  type CvAnalysisResponse,
} from "@/lib/recruitment-admin-api";
import { ArrowLeft, Download, FileText, Send, Trash2, User, CheckCircle, Clock, Sparkles } from "lucide-react";

import { formatEnumLabel } from "@/lib/recruitment-formatters";

export function ApplicationDetailPage({ applicationId }: { applicationId: string }) {
  const t = useTranslations("Recruitment");
  const format = useFormatter();
  const { request } = useApiClient();
  const router = useRouter();

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
    void load();
  }, [load]);

  const handleTransition = async (targetStatus: string) => {
    setActing(true);
    setError("");
    setActionSuccess("");
    try {
      await transitionApplication(request, applicationId, targetStatus);
      setActionSuccess(`Application status updated to ${formatEnumLabel(targetStatus)}.`);
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
      setActionSuccess("Interview invitation issued successfully.");
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
      setActionSuccess("Completion link sent to candidate email.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleDeleteCv = async () => {
    if (!window.confirm("Permanently delete candidate CV document for privacy compliance?")) return;
    setActing(true);
    try {
      await deleteRecruitmentCv(request, applicationId);
      setActionSuccess("Candidate CV deleted.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setActing(false);
    }
  };

  const handleDeleteApp = async () => {
    if (!window.confirm("Permanently delete this application?")) return;
    try {
      await deleteRecruitmentApplication(request, applicationId);
      router.push("/recruitment/applications");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    }
  };

  if (loading) return <p className="p-6 text-sm text-muted-foreground">{t("loading")}</p>;
  if (!detail) return <p className="p-6 text-sm text-red-600">{error || t("loadError")}</p>;

  const { application: app, candidate, screeningQuestions, screeningAnswers } = detail;
  const answerMap = new Map(screeningAnswers.map((a) => [a.questionId, a.optionId]));

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b pb-4">
        <div>
          <Button variant="ghost" size="sm" nativeButton={false} render={<Link href="/recruitment/applications" />}>
            <ArrowLeft className="mr-1 h-4 w-4" /> Back to Applications
          </Button>
          <div className="flex items-center gap-3 mt-2">
            <h2 className="text-2xl font-bold">{candidate.fullName}</h2>
            <Badge variant="outline" className="text-sm font-semibold">
              {formatEnumLabel(app.status)}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Applying for <span className="font-semibold text-foreground">{app.jobTitle}</span> · {candidate.email}
          </p>
        </div>

        {/* Header Action Buttons */}
        <div className="flex flex-wrap gap-2">
          {app.status === "AWAITING_CANDIDATE" && (
            <Button variant="outline" disabled={cooldown || acting} onClick={() => void handleResendCompletionLink()}>
              <Send className="mr-1 h-4 w-4" />
              {cooldown ? "Resent (60s)" : "Send Completion Link"}
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
                  Under Review
                </Button>
              )}
              <Button variant="outline" size="sm" disabled={acting} onClick={() => void handleTransition("SHORTLISTED")}>
                Shortlist
              </Button>
              <Button variant="destructive" size="sm" disabled={acting} onClick={() => void handleTransition("REJECTED")}>
                Reject
              </Button>
            </>
          )}

          <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50" onClick={() => void handleDeleteApp()}>
            <Trash2 className="h-4 w-4" />
          </Button>
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
                <User className="h-4 w-4 text-indigo-600" /> Candidate Profile
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div>
                <p className="text-xs text-muted-foreground">Full Name</p>
                <p className="font-medium">{candidate.fullName}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Email</p>
                <p className="font-medium">{candidate.email}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Phone</p>
                <p className="font-medium">{candidate.phone || "—"}</p>
              </div>
              {candidate.notes && (
                <div>
                  <p className="text-xs text-muted-foreground">Notes</p>
                  <p className="text-xs bg-slate-50 p-2 rounded border">{candidate.notes}</p>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <Clock className="h-4 w-4 text-indigo-600" /> Lifecycle & Status
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div>
                <p className="text-xs text-muted-foreground">Application Status</p>
                <p className="font-semibold">{formatEnumLabel(app.status)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Submitted At</p>
                <p>{app.submittedAt ? format.dateTime(new Date(app.submittedAt), { dateStyle: "long", timeStyle: "short" }) : "Not submitted yet"}</p>
              </div>
              {app.verifiedAt && (
                <div>
                  <p className="text-xs text-muted-foreground">Verified At</p>
                  <p>{format.dateTime(new Date(app.verifiedAt), { dateStyle: "medium", timeStyle: "short" })}</p>
                </div>
              )}
              {app.withdrawnAt && (
                <div>
                  <p className="text-xs text-muted-foreground">Withdrawn At</p>
                  <p className="text-red-600">{format.dateTime(new Date(app.withdrawnAt), { dateStyle: "medium", timeStyle: "short" })}</p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* CV Attachment Card */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <FileText className="h-4 w-4 text-indigo-600" /> Curriculum Vitae (CV)
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              {app.cvPresent ? (
                <>
                  <p className="text-xs text-green-700 bg-green-50 p-2 rounded border border-green-200">
                    CV document uploaded and available.
                  </p>
                  <div className="flex flex-col gap-2">
                    <Button variant="outline" size="sm" nativeButton={false} render={<a href={cvUrl(app.id)} target="_blank" rel="noreferrer" />}>
                      <Download className="mr-2 h-4 w-4" /> Download CV
                    </Button>
                    <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50" onClick={() => void handleDeleteCv()}>
                      <Trash2 className="mr-2 h-4 w-4" /> Delete CV (Privacy)
                    </Button>
                  </div>
                </>
              ) : (
                <p className="text-xs text-muted-foreground">No CV file uploaded for this application.</p>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Screening Answers & CV AI Evidence */}
        <div className="md:col-span-2 space-y-6">
          <Tabs defaultValue="screening">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="screening">Screening Answers ({screeningQuestions.length})</TabsTrigger>
              <TabsTrigger value="ai">
                <Sparkles className="mr-1 h-3.5 w-3.5" /> CV AI Review
              </TabsTrigger>
            </TabsList>

            <TabsContent value="screening" className="mt-4 space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Screening Evidence</CardTitle>
                  <CardDescription>Review candidate responses to job screening questions.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  {screeningQuestions.length === 0 && <p className="text-sm text-muted-foreground">No screening questions were required for this job.</p>}
                  {screeningQuestions.map((q, idx) => {
                    const chosenOptionId = answerMap.get(q.questionId);
                    const chosenOption = q.options.find((opt) => opt.optionId === chosenOptionId);
                    return (
                      <div key={q.questionId} className="rounded-lg border p-4 bg-slate-50/50 space-y-2">
                        <p className="text-sm font-semibold text-foreground">
                          {idx + 1}. {q.prompt}
                        </p>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground">Candidate Answer:</span>
                          <span className="font-medium text-sm text-indigo-700 bg-indigo-50 px-2 py-0.5 rounded border border-indigo-200">
                            {chosenOption ? chosenOption.label : "Not answered"}
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
                  <CardTitle className="text-base flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-indigo-600" /> AI CV Analysis & Personalization
                  </CardTitle>
                  <CardDescription>Extracted skills, evidence excerpts, and candidate personalized questions.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  {!cvAnalysis ? (
                    <p className="text-sm text-muted-foreground">
                      {app.cvPresent ? "CV AI analysis is being generated or was disabled." : "No CV available for AI analysis."}
                    </p>
                  ) : (
                    <>
                      {/* Summary */}
                      {cvAnalysis.summary && (
                        <div className="rounded-md bg-slate-50 p-4 border">
                          <h4 className="font-semibold text-sm mb-1">Executive Summary</h4>
                          <p className="text-sm text-slate-700 leading-relaxed">{cvAnalysis.summary}</p>
                        </div>
                      )}

                      {/* Skills */}
                      {cvAnalysis.skills && cvAnalysis.skills.length > 0 && (
                        <div>
                          <h4 className="font-semibold text-sm mb-2">Extracted Skills</h4>
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
                          <h4 className="font-semibold text-sm mb-2">Personalized Interview Questions</h4>
                          <div className="space-y-2">
                            {cvAnalysis.personalizedQuestions.map((pq, idx) => (
                              <div key={idx} className="rounded-md border p-3 bg-indigo-50/40 text-sm">
                                <p className="font-medium text-foreground">{pq.prompt}</p>
                                <div className="flex justify-between text-xs text-muted-foreground mt-1">
                                  <span>Competency: {pq.competency}</span>
                                  <span>Rubric: {pq.rubric}</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Evidence Excerpts */}
                      {cvAnalysis.evidence && cvAnalysis.evidence.length > 0 && (
                        <div>
                          <h4 className="font-semibold text-sm mb-2">Verified Excerpts</h4>
                          <div className="space-y-2">
                            {cvAnalysis.evidence.map((ev, idx) => (
                              <div key={idx} className="rounded border p-2 text-xs bg-slate-50 italic">
                                "{ev.excerpt}" — <span className="text-muted-foreground font-normal">{ev.sourceLocation}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
  );
}

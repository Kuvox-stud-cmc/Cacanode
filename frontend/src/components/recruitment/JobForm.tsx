"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { ExternalLink, Plus, Trash2 } from "lucide-react";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RichJobDescriptionEditor } from "@/components/recruitment/RichJobDescriptionEditor";
import { useLocaleChangeDraft } from "@/hooks/useLocaleChangeDraft";
import { useRecruitmentConfirmation } from "@/components/recruitment/useRecruitmentConfirmation";
import { useAuthStore } from "@/components/providers/StoreProvider";
import {
  createRecruitmentJob,
  deleteRecruitmentJob,
  getRecruitmentJob,
  jobAction,
  listRecruitmentTemplates,
  listTemplateRevisions,
  updateRecruitmentJob,
  type JobWrite,
  type RecruitmentJob,
  type RevisionResponse,
  type ScreeningQuestion,
} from "@/lib/recruitment-admin-api";

type RevisionChoice = RevisionResponse & { templateName: string; archived: boolean };
type SaveIntent = "draft" | "preview" | "publish";

const employmentTypes = ["FULL_TIME", "PART_TIME", "CONTRACT", "TEMPORARY", "INTERNSHIP"];
const workModes = ["ONSITE", "REMOTE", "HYBRID"];
const experienceLevels = ["ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD", "EXECUTIVE"];
const automationModes = ["MANUAL", "AUTO_INVITE_ALL", "AUTO_INVITE_MATCHING"];
const cvAiModes = ["OFF", "SUMMARY_ONLY", "PERSONALIZED_QUESTIONS"];

const emptyJob: JobWrite = {
  title: "",
  description: "",
  descriptionHtml: null,
  department: null,
  location: null,
  employmentType: null,
  workMode: null,
  experienceLevel: null,
  language: "en-US",
  cvPolicy: "OPTIONAL",
  automationModeOverride: null,
  cvAiModeOverride: null,
  templateRevisionId: null,
  closingAt: null,
  screeningQuestions: [],
};

function uuid() {
  return crypto.randomUUID();
}

function newQuestion(): ScreeningQuestion {
  const first = uuid();
  const second = uuid();
  return {
    questionId: uuid(),
    prompt: "",
    options: [{ optionId: first, label: "" }, { optionId: second, label: "" }],
    acceptedOptionIds: [first],
  };
}

function toWrite(job: RecruitmentJob): JobWrite {
  return {
    title: job.title,
    description: job.description,
    descriptionHtml: job.descriptionHtml,
    department: job.department,
    location: job.location,
    employmentType: job.employmentType,
    workMode: job.workMode,
    experienceLevel: job.experienceLevel,
    language: job.language,
    cvPolicy: job.cvPolicy,
    automationModeOverride: job.automationModeOverride,
    cvAiModeOverride: job.cvAiModeOverride,
    templateRevisionId: job.templateRevisionId,
    closingAt: job.closingAt,
    screeningQuestions: job.screeningQuestions,
  };
}

export function validateJob(value: JobWrite, publishing: boolean): string | null {
  if (!value.title.trim() || !value.description.trim()) return "titleDescription";
  if (publishing && (!value.templateRevisionId || !value.closingAt)) return "publishRequirements";
  if (publishing && value.closingAt && new Date(value.closingAt).getTime() <= Date.now()) return "futureClosing";
  if (value.screeningQuestions.length > 10) return "screeningLimit";
  for (const question of value.screeningQuestions) {
    if (!question.prompt.trim() || question.options.length < 2 || question.options.length > 10) return "screeningInvalid";
    if (question.options.some((option) => !option.label.trim()) || question.acceptedOptionIds.length === 0) return "screeningInvalid";
  }
  if (value.automationModeOverride === "AUTO_INVITE_MATCHING" && value.screeningQuestions.length === 0) return "matchingNeedsScreening";
  return null;
}

import { formatEnumLabel } from "@/lib/recruitment-formatters";
import { useLocale } from "next-intl";

function SelectField({ id, label, value, values, allowDefault = false, disabled, onChange }: {
  id: string; label: string; value: string | null; values: string[]; allowDefault?: boolean; disabled?: boolean;
  onChange: (value: string | null) => void;
}) {
  const locale = useLocale();
  return <div className="space-y-2">
    <Label htmlFor={id}>{label}</Label>
    <select id={id} className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm disabled:opacity-60" value={value ?? ""} disabled={disabled} onChange={(event) => onChange(event.target.value || null)}>
      <option value="">{allowDefault ? (locale.startsWith("vi") ? "Mặc định tổ chức" : "Tenant default") : "—"}</option>
      {values.map((item) => <option key={item} value={item}>{formatEnumLabel(item, locale)}</option>)}
    </select>
  </div>;
}

export function JobForm({ jobId }: { jobId?: string }) {
  const t = useTranslations("Recruitment");
  const locale = useLocale();
  const { request } = useApiClient();
  const router = useRouter();
  const { confirm, confirmationDialog } = useRecruitmentConfirmation();
  const tenantId = useAuthStore((state) => state.user?.tenantId);
  const [job, setJob] = useState<JobWrite>(emptyJob);
  const [record, setRecord] = useState<RecruitmentJob | null>(null);
  const [revisions, setRevisions] = useState<RevisionChoice[]>([]);
  const [loading, setLoading] = useState(Boolean(jobId));
  const [saving, setSaving] = useState<SaveIntent | "action" | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const templates = await listRecruitmentTemplates(request, { page: 0, size: 100, archived: false, sort: "name", direction: "asc" });
      const revisionGroups = await Promise.all(templates.items.map(async (template) => {
        const items = await listTemplateRevisions(request, template.id);
        return items.map((revision) => ({ ...revision, templateName: template.name, archived: template.archived }));
      }));
      setRevisions(revisionGroups.flat());
      if (jobId) {
        const value = await getRecruitmentJob(request, jobId);
        setRecord(value);
        setJob(toWrite(value));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [jobId, request, t]);

  useEffect(() => {
    // Client-side route data is loaded when the job identity changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const editable = !record || record.status === "DRAFT" || record.status === "PAUSED";
  const canPublish = !record || record.status === "DRAFT" || record.status === "PAUSED";
  const clearLocaleDraft = useLocaleChangeDraft(
    `recruitment:job:${tenantId ?? "unknown"}:${jobId ?? "new"}`,
    job,
    setJob,
    Boolean(tenantId) && !loading && editable,
  );
  const selectedRevision = useMemo(() => revisions.find((item) => item.id === job.templateRevisionId), [job.templateRevisionId, revisions]);

  function updateQuestion(index: number, value: ScreeningQuestion) {
    setJob((current) => ({ ...current, screeningQuestions: current.screeningQuestions.map((item, itemIndex) => itemIndex === index ? value : item) }));
  }

  async function save(intent: SaveIntent) {
    const validation = validateJob(job, intent === "publish");
    if (validation) { setError(t(`forms.errors.${validation}` as Parameters<typeof t>[0])); return; }
    const previewWindow = intent === "preview" ? window.open("about:blank", "_blank") : null;
    if (previewWindow) previewWindow.opener = null;
    setSaving(intent);
    setError("");
    try {
      const saved = record
        ? await updateRecruitmentJob(request, record.id, job)
        : await createRecruitmentJob(request, job);
      const finalValue = intent === "publish" ? await jobAction(request, saved.id, "publish") : saved;
      clearLocaleDraft();
      setRecord(finalValue);
      setJob(toWrite(finalValue));
      if (!jobId) router.replace(`/recruitment/jobs/${finalValue.id}`);
      if (intent === "preview") {
        const previewUrl = `${locale.startsWith("vi") ? "/vi" : ""}/recruitment/jobs/${finalValue.id}/preview`;
        if (previewWindow) previewWindow.location.replace(previewUrl);
        else window.open(previewUrl, "_blank", "noopener,noreferrer");
      }
      router.refresh();
    } catch (cause) {
      previewWindow?.close();
      setError(cause instanceof Error ? cause.message : t("forms.createError"));
    } finally {
      setSaving(null);
    }
  }

  async function runAction(action: "pause" | "close" | "archive") {
    if (!record || !await confirm({ title: `${t(`actions.${action}`)}: ${record.title}`, description: t(`forms.confirm.${action}`), confirmLabel: t(`actions.${action}`), destructive: true })) return;
    setSaving("action"); setError("");
    try {
      const value = await jobAction(request, record.id, action);
      clearLocaleDraft();
      setRecord(value); setJob(toWrite(value)); router.refresh();
    } catch (cause) { setError(cause instanceof Error ? cause.message : t("forms.createError")); }
    finally { setSaving(null); }
  }

  async function remove() {
    if (!record || !await confirm({ title: `${t("forms.delete")}: ${record.title}`, description: t("forms.confirm.deleteJob"), confirmLabel: t("forms.delete"), destructive: true })) return;
    setSaving("action"); setError("");
    try { await deleteRecruitmentJob(request, record.id); clearLocaleDraft(); router.push("/recruitment/jobs"); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t("forms.createError")); setSaving(null); }
  }

  if (loading) return <p role="status" className="p-6 text-sm text-muted-foreground">{t("loading")}</p>;

  return <div className="mx-auto max-w-5xl space-y-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><div className="flex items-center gap-2"><h3 className="text-xl font-semibold">{record ? record.title : t("forms.createJob")}</h3>{record && <Badge variant="outline">{formatEnumLabel(record.status, locale)}</Badge>}</div><p className="text-sm text-slate-600">{t("forms.jobHelp")}</p></div>
      <div className="flex flex-wrap gap-2">
        {record?.status === "PUBLISHED" && <Link href={`/jobs/${record.publicId}`} target="_blank" rel="noopener noreferrer" className={buttonVariants({ variant: "outline" })}><ExternalLink />{t("forms.publicPreview")}</Link>}
        {record?.status === "DRAFT" && <Button variant="destructive" onClick={() => void remove()} disabled={Boolean(saving)}><Trash2 />{t("forms.delete")}</Button>}
        {record?.status === "PUBLISHED" && <><Button variant="outline" onClick={() => void runAction("pause")} disabled={Boolean(saving)}>{t("actions.pause")}</Button><Button variant="destructive" onClick={() => void runAction("close")} disabled={Boolean(saving)}>{t("actions.close")}</Button></>}
        {record?.status === "PAUSED" && <Button variant="destructive" onClick={() => void runAction("close")} disabled={Boolean(saving)}>{t("actions.close")}</Button>}
        {record?.status === "CLOSED" && <Button variant="outline" onClick={() => void runAction("archive")} disabled={Boolean(saving)}>{t("actions.archive")}</Button>}
      </div>
    </div>
    {error && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}

    <Card><CardHeader><CardTitle>{t("forms.jobBasics")}</CardTitle><CardDescription>{t("forms.jobBasicsHelp")}</CardDescription></CardHeader><CardContent className="grid gap-4 md:grid-cols-2">
      <div className="space-y-2 md:col-span-2"><Label htmlFor="job-title">{t("fields.title")} *</Label><Input id="job-title" required disabled={!editable} value={job.title} onChange={(event) => setJob({ ...job, title: event.target.value })} /></div>
      <div className="space-y-2 md:col-span-2"><Label htmlFor="job-description">{t("fields.description")} *</Label><RichJobDescriptionEditor id="job-description" disabled={!editable} locale={locale} value={job.descriptionHtml} legacyPlainText={job.description} onChange={(descriptionHtml, description) => setJob((current) => ({ ...current, descriptionHtml, description }))} /><p className="text-xs text-muted-foreground">{t("forms.richDescriptionHelp")}</p></div>
      <div className="space-y-2"><Label htmlFor="department">{t("fields.department")}</Label><Input id="department" disabled={!editable} value={job.department ?? ""} onChange={(event) => setJob({ ...job, department: event.target.value || null })} /></div>
      <div className="space-y-2"><Label htmlFor="location">{t("fields.location")}</Label><Input id="location" disabled={!editable} value={job.location ?? ""} onChange={(event) => setJob({ ...job, location: event.target.value || null })} /></div>
      <SelectField id="employment" label={t("forms.employmentType")} value={job.employmentType} values={employmentTypes} disabled={!editable} onChange={(value) => setJob({ ...job, employmentType: value })} />
      <SelectField id="work-mode" label={t("forms.workMode")} value={job.workMode} values={workModes} disabled={!editable} onChange={(value) => setJob({ ...job, workMode: value })} />
      <SelectField id="experience" label={t("forms.experienceLevel")} value={job.experienceLevel} values={experienceLevels} disabled={!editable} onChange={(value) => setJob({ ...job, experienceLevel: value })} />
      <SelectField id="language" label={t("fields.language")} value={job.language} values={["en-US", "vi-VN"]} disabled={!editable} onChange={(value) => setJob({ ...job, language: (value ?? "en-US") as JobWrite["language"] })} />
    </CardContent></Card>

    <Card><CardHeader><CardTitle>{t("forms.interviewConfiguration")}</CardTitle><CardDescription>{t("forms.interviewConfigurationHelp")}</CardDescription></CardHeader><CardContent className="grid gap-4 md:grid-cols-2">
      <div className="space-y-2 md:col-span-2"><Label htmlFor="revision">{t("forms.templateRevision")}</Label><select id="revision" disabled={!editable} className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm disabled:opacity-60" value={job.templateRevisionId ?? ""} onChange={(event) => setJob({ ...job, templateRevisionId: event.target.value || null })}><option value="">{t("forms.tenantDefault")}</option>{revisions.map((revision) => <option key={revision.id} value={revision.id}>{revision.templateName} · v{revision.revisionNumber}</option>)}</select>{selectedRevision && <p className="text-xs text-muted-foreground">SHA-256: {selectedRevision.contentSha256.slice(0, 12)}…</p>}</div>
      <SelectField id="cv-policy" label={t("fields.cvPolicy")} value={job.cvPolicy} values={["DISABLED", "OPTIONAL", "REQUIRED"]} disabled={!editable} onChange={(value) => setJob({ ...job, cvPolicy: value ?? "OPTIONAL" })} />
      <SelectField id="automation" label={t("forms.automationOverride")} value={job.automationModeOverride} values={automationModes} allowDefault disabled={!editable} onChange={(value) => setJob({ ...job, automationModeOverride: value })} />
      <SelectField id="cv-ai" label={t("forms.cvAiOverride")} value={job.cvAiModeOverride} values={cvAiModes} allowDefault disabled={!editable} onChange={(value) => setJob({ ...job, cvAiModeOverride: value })} />
      <div className="space-y-2"><Label htmlFor="closing">{t("forms.closingAt")}</Label><Input id="closing" type="datetime-local" disabled={!editable} value={job.closingAt?.slice(0, 16) ?? ""} onChange={(event) => setJob({ ...job, closingAt: event.target.value || null })} /></div>
    </CardContent></Card>

    <Card><CardHeader><CardTitle>{t("forms.screeningQuestions")}</CardTitle><CardDescription>{t("forms.screeningHelp")}</CardDescription></CardHeader><CardContent className="space-y-4">
      {job.screeningQuestions.map((question, questionIndex) => <fieldset key={question.questionId} disabled={!editable} className="space-y-3 rounded-lg border p-4"><legend className="px-1 text-sm font-semibold">{t("forms.questionNumber", { number: questionIndex + 1 })}</legend>
        <div className="flex gap-2"><Input aria-label={t("forms.questionPrompt")} value={question.prompt} onChange={(event) => updateQuestion(questionIndex, { ...question, prompt: event.target.value })} /><Button type="button" size="icon" variant="ghost" aria-label={t("forms.removeQuestion")} onClick={() => setJob({ ...job, screeningQuestions: job.screeningQuestions.filter((_, index) => index !== questionIndex) })}><Trash2 /></Button></div>
        <div className="space-y-2">{question.options.map((option, optionIndex) => <div key={option.optionId} className="flex items-center gap-2"><input type="checkbox" aria-label={t("forms.acceptedOption")} checked={question.acceptedOptionIds.includes(option.optionId)} onChange={(event) => updateQuestion(questionIndex, { ...question, acceptedOptionIds: event.target.checked ? [...question.acceptedOptionIds, option.optionId] : question.acceptedOptionIds.filter((id) => id !== option.optionId) })} /><Input aria-label={t("forms.optionNumber", { number: optionIndex + 1 })} value={option.label} onChange={(event) => updateQuestion(questionIndex, { ...question, options: question.options.map((item, index) => index === optionIndex ? { ...item, label: event.target.value } : item) })} /><Button type="button" size="icon" variant="ghost" aria-label={t("forms.removeOption")} disabled={question.options.length <= 2} onClick={() => updateQuestion(questionIndex, { ...question, options: question.options.filter((_, index) => index !== optionIndex), acceptedOptionIds: question.acceptedOptionIds.filter((id) => id !== option.optionId) })}><Trash2 /></Button></div>)}</div>
        <Button type="button" variant="outline" size="sm" disabled={question.options.length >= 10} onClick={() => updateQuestion(questionIndex, { ...question, options: [...question.options, { optionId: uuid(), label: "" }] })}><Plus />{t("forms.addOption")}</Button>
      </fieldset>)}
      {editable && <Button type="button" variant="outline" disabled={job.screeningQuestions.length >= 10} onClick={() => setJob({ ...job, screeningQuestions: [...job.screeningQuestions, newQuestion()] })}><Plus />{t("forms.addQuestion")}</Button>}
    </CardContent></Card>

    {editable && <div className="flex flex-wrap justify-end gap-2"><Button variant="outline" nativeButton={false} render={<Link href="/recruitment/jobs" onClick={clearLocaleDraft} />}>{t("actions.cancel")}</Button><Button variant="outline" disabled={Boolean(saving)} onClick={() => void save("draft")}>{saving === "draft" ? t("actions.saving") : t("forms.saveDraft")}</Button><Button variant="outline" disabled={Boolean(saving)} onClick={() => void save("preview")}>{saving === "preview" ? t("actions.saving") : t("forms.savePreview")}</Button>{canPublish && <Button disabled={Boolean(saving)} onClick={() => void save("publish")}>{saving === "publish" ? t("actions.saving") : record?.status === "PAUSED" ? t("forms.saveRepublish") : t("forms.savePublish")}</Button>}</div>}
    {confirmationDialog}
  </div>;
}

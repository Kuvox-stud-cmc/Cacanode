"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { ArrowDown, ArrowUp, Copy, Plus, Trash2 } from "lucide-react";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  addTemplateRevision,
  archiveRecruitmentTemplate,
  createRecruitmentTemplate,
  getRecruitmentTemplate,
  listTemplateRevisions,
  patchRecruitmentTemplate,
  type Question,
  type RecruitmentTemplate,
  type RevisionContent,
  type RevisionResponse,
  type Section,
  type TemplateCreate,
} from "@/lib/recruitment-admin-api";

function uuid() { return crypto.randomUUID(); }

function newQuestion(): Question {
  return { questionId: uuid(), position: 1, prompt: "", competency: "", rubric: "", followUpLimit: 1, source: "TEMPLATE", evidence: null };
}

function newSection(locale: "vi-VN" | "en-US", kind = "CORE"): Section {
  return { sectionId: uuid(), position: 1, kind, languageTag: kind === "ENGLISH_SCREEN" ? "en-US" : locale, durationLimitSeconds: 300, transitionText: null, questions: [newQuestion()] };
}

function initialContent(locale: "vi-VN" | "en-US"): RevisionContent {
  return {
    introductionText: locale === "vi-VN" ? "Chào mừng bạn đến với buổi phỏng vấn." : "Welcome to the interview.",
    disclosureText: locale === "vi-VN" ? "Cuộc phỏng vấn có thể được ghi âm theo chính sách đã thông báo." : "This interview may be recorded under the disclosed policy.",
    closingText: locale === "vi-VN" ? "Cảm ơn bạn đã dành thời gian." : "Thank you for your time.",
    durationLimitSeconds: 1800,
    interactionLimits: { repetitionLimit: 3, clarificationLimit: 3, silenceTimeoutSeconds: 15, silencePromptLimit: 2 },
    sections: [newSection(locale)],
  };
}

export function normalizeTemplateContent(content: RevisionContent): RevisionContent {
  return {
    ...content,
    sections: content.sections.map((section, sectionIndex) => ({
      ...section,
      position: sectionIndex + 1,
      transitionText: section.transitionText?.trim() || null,
      questions: section.questions.map((question, questionIndex) => ({ ...question, position: questionIndex + 1, source: "TEMPLATE", evidence: question.evidence?.trim() || null })),
    })),
  };
}

export function validateTemplate(locale: "vi-VN" | "en-US", raw: RevisionContent): string | null {
  const content = normalizeTemplateContent(raw);
  if (!content.introductionText.trim() || !content.disclosureText.trim() || !content.closingText.trim()) return "templateTexts";
  if (content.durationLimitSeconds < 1 || content.sections.length === 0) return "templateSections";
  if (content.sections.reduce((sum, section) => sum + section.durationLimitSeconds, 0) > content.durationLimitSeconds) return "sectionDuration";
  let englishScreens = 0;
  for (let index = 0; index < content.sections.length; index++) {
    const section = content.sections[index];
    if (section.questions.length === 0 || section.durationLimitSeconds < 1) return "sectionQuestions";
    if (section.questions.some((question) => !question.prompt.trim() || !question.competency.trim() || !question.rubric.trim())) return "questionContent";
    if (locale === "en-US" && (section.kind !== "CORE" || section.languageTag !== "en-US")) return "englishTemplateRule";
    if (locale === "vi-VN" && section.kind === "ENGLISH_SCREEN") {
      englishScreens++;
      if (section.languageTag !== "en-US" || section.questions.length < 2 || section.questions.length > 5 || section.durationLimitSeconds > 300 || !section.transitionText?.trim()) return "vietnameseEnglishRule";
      if (index + 1 < content.sections.length && !content.sections[index + 1].transitionText?.trim()) return "returnTransition";
    } else if (locale === "vi-VN" && section.languageTag !== "vi-VN") return "vietnameseCoreRule";
  }
  if (englishScreens > 1) return "englishScreenLimit";
  return null;
}

function NumberField({ id, label, value, min = 0, max, disabled, onChange }: { id: string; label: string; value: number; min?: number; max?: number; disabled?: boolean; onChange: (value: number) => void }) {
  return <div className="space-y-2"><Label htmlFor={id}>{label}</Label><Input id={id} type="number" min={min} max={max} disabled={disabled} value={value} onChange={(event) => onChange(Number(event.target.value))} /></div>;
}

export function TemplateForm({ templateId }: { templateId?: string }) {
  const t = useTranslations("Recruitment");
  const { request } = useApiClient();
  const router = useRouter();
  const [template, setTemplate] = useState<TemplateCreate>({ name: "", description: "", locale: "en-US", content: initialContent("en-US") });
  const [record, setRecord] = useState<RecruitmentTemplate | null>(null);
  const [revisions, setRevisions] = useState<RevisionResponse[]>([]);
  const [viewingRevisionId, setViewingRevisionId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState(true);
  const [loading, setLoading] = useState(Boolean(templateId));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    if (!templateId) return;
    setLoading(true); setError("");
    try {
      const [metadata, history] = await Promise.all([getRecruitmentTemplate(request, templateId), listTemplateRevisions(request, templateId)]);
      const latest = history[0];
      setRecord(metadata); setRevisions(history); setViewingRevisionId(latest?.id ?? null); setEditingContent(false);
      setTemplate({ name: metadata.name, description: metadata.description, locale: metadata.locale, content: latest?.content ?? initialContent(metadata.locale) });
    } catch (cause) { setError(cause instanceof Error ? cause.message : t("loadError")); }
    finally { setLoading(false); }
  }, [request, t, templateId]);

  useEffect(() => { void load(); }, [load]);

  const displayedRevision = useMemo(() => revisions.find((revision) => revision.id === viewingRevisionId), [revisions, viewingRevisionId]);
  const readOnly = Boolean(record?.archived) || (Boolean(record) && !editingContent);

  function setContent(content: RevisionContent) { setTemplate((current) => ({ ...current, content })); }
  function updateSection(index: number, value: Section) { setContent({ ...template.content, sections: template.content.sections.map((section, sectionIndex) => sectionIndex === index ? value : section) }); }
  function moveSection(index: number, delta: number) {
    const target = index + delta; if (target < 0 || target >= template.content.sections.length) return;
    const sections = [...template.content.sections]; [sections[index], sections[target]] = [sections[target], sections[index]]; setContent({ ...template.content, sections });
  }
  function updateQuestion(sectionIndex: number, questionIndex: number, value: Question) {
    const section = template.content.sections[sectionIndex];
    updateSection(sectionIndex, { ...section, questions: section.questions.map((question, index) => index === questionIndex ? value : question) });
  }
  function moveQuestion(sectionIndex: number, questionIndex: number, delta: number) {
    const section = template.content.sections[sectionIndex]; const target = questionIndex + delta;
    if (target < 0 || target >= section.questions.length) return;
    const questions = [...section.questions]; [questions[questionIndex], questions[target]] = [questions[target], questions[questionIndex]]; updateSection(sectionIndex, { ...section, questions });
  }

  function selectRevision(revisionId: string) {
    const revision = revisions.find((item) => item.id === revisionId); if (!revision) return;
    setViewingRevisionId(revisionId); setEditingContent(false); setTemplate((current) => ({ ...current, content: revision.content })); setError("");
  }

  function cloneLatest() {
    const latest = revisions[0]; if (!latest) return;
    setViewingRevisionId(latest.id); setTemplate((current) => ({ ...current, content: structuredClone(latest.content) })); setEditingContent(true); setError("");
  }

  async function save() {
    const validation = validateTemplate(template.locale, template.content);
    if (!template.name.trim()) { setError(t("forms.errors.templateName")); return; }
    if (validation) { setError(t(`forms.errors.${validation}` as Parameters<typeof t>[0])); return; }
    setSaving(true); setError("");
    try {
      const content = normalizeTemplateContent(template.content);
      if (!record) {
        const value = await createRecruitmentTemplate(request, { ...template, content });
        router.replace(`/recruitment/templates/${value.id}`); router.refresh();
      } else {
        const metadataChanged = record.name !== template.name.trim() || (record.description ?? "") !== (template.description ?? "").trim();
        if (metadataChanged) await patchRecruitmentTemplate(request, record.id, { name: template.name, description: template.description });
        if (editingContent) await addTemplateRevision(request, record.id, { content });
        await load(); router.refresh();
      }
    } catch (cause) { setError(cause instanceof Error ? cause.message : t("forms.createError")); }
    finally { setSaving(false); }
  }

  async function archive() {
    if (!record || !window.confirm(t("forms.confirm.archiveTemplate"))) return;
    setSaving(true); setError("");
    try { await archiveRecruitmentTemplate(request, record.id); await load(); router.refresh(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : t("forms.createError")); setSaving(false); }
  }

  if (loading) return <p role="status" className="p-6 text-sm text-muted-foreground">{t("loading")}</p>;

  return <div className="mx-auto max-w-5xl space-y-4">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex items-center gap-2"><h3 className="text-xl font-semibold">{record ? record.name : t("forms.createTemplate")}</h3>{record && <Badge variant="outline">{record.archived ? t("actions.archive") : `v${record.latestRevisionNumber}`}</Badge>}</div><p className="text-sm text-muted-foreground">{t("forms.templateHelp")}</p></div><div className="flex gap-2">{record && !record.archived && <Button variant="outline" onClick={() => void archive()} disabled={saving}>{t("forms.archiveTemplate")}</Button>}</div></div>
    {error && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}

    <Card><CardHeader><CardTitle>{t("forms.templateMetadata")}</CardTitle><CardDescription>{t("forms.metadataHelp")}</CardDescription></CardHeader><CardContent className="grid gap-4 md:grid-cols-2">
      <div className="space-y-2"><Label htmlFor="template-name">{t("fields.name")} *</Label><Input id="template-name" disabled={record?.archived} value={template.name} onChange={(event) => setTemplate({ ...template, name: event.target.value })} /></div>
      <div className="space-y-2"><Label htmlFor="template-locale">{t("fields.language")}</Label><select id="template-locale" disabled={Boolean(record)} className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm disabled:opacity-60" value={template.locale} onChange={(event) => { const locale = event.target.value as TemplateCreate["locale"]; setTemplate({ ...template, locale, content: initialContent(locale) }); }}><option value="en-US">{t("forms.enUS")}</option><option value="vi-VN">{t("forms.viVN")}</option></select></div>
      <div className="space-y-2 md:col-span-2"><Label htmlFor="template-description">{t("fields.description")}</Label><textarea id="template-description" disabled={record?.archived} className="min-h-20 w-full rounded-lg border border-input bg-background p-3 text-sm disabled:opacity-60" value={template.description ?? ""} onChange={(event) => setTemplate({ ...template, description: event.target.value || null })} /></div>
    </CardContent></Card>

    {record && <Card><CardHeader><CardTitle>{t("forms.revisionHistory")}</CardTitle><CardDescription>{t("forms.revisionHelp")}</CardDescription></CardHeader><CardContent className="flex flex-wrap items-end gap-3"><div className="min-w-64 space-y-2"><Label htmlFor="revision-history">{t("forms.viewRevision")}</Label><select id="revision-history" className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm" value={viewingRevisionId ?? ""} onChange={(event) => selectRevision(event.target.value)}>{revisions.map((revision) => <option key={revision.id} value={revision.id}>v{revision.revisionNumber} · {new Date(revision.createdAt).toLocaleString()}</option>)}</select></div>{displayedRevision && <p className="pb-2 text-xs text-muted-foreground">{displayedRevision.contentSha256.slice(0, 20)}…</p>}{!record.archived && <Button variant="outline" onClick={cloneLatest}><Copy />{t("forms.cloneLatest")}</Button>}</CardContent></Card>}

    <Card><CardHeader><CardTitle>{t("forms.spokenText")}</CardTitle><CardDescription>{readOnly ? t("forms.readOnlyRevision") : t("forms.spokenTextHelp")}</CardDescription></CardHeader><CardContent className="space-y-4">
      {(["introductionText", "disclosureText", "closingText"] as const).map((key) => <div key={key} className="space-y-2"><Label htmlFor={key}>{t(`forms.${key}`)}</Label><textarea id={key} disabled={readOnly} className="min-h-20 w-full rounded-lg border border-input bg-background p-3 text-sm disabled:opacity-60" value={template.content[key]} onChange={(event) => setContent({ ...template.content, [key]: event.target.value })} /></div>)}
      <div className="grid gap-4 md:grid-cols-3"><NumberField id="total-duration" label={t("forms.totalDuration")} min={1} disabled={readOnly} value={template.content.durationLimitSeconds} onChange={(value) => setContent({ ...template.content, durationLimitSeconds: value })} /><NumberField id="repetition" label={t("forms.repetitionLimit")} disabled={readOnly} value={template.content.interactionLimits.repetitionLimit} onChange={(value) => setContent({ ...template.content, interactionLimits: { ...template.content.interactionLimits, repetitionLimit: value } })} /><NumberField id="clarification" label={t("forms.clarificationLimit")} disabled={readOnly} value={template.content.interactionLimits.clarificationLimit} onChange={(value) => setContent({ ...template.content, interactionLimits: { ...template.content.interactionLimits, clarificationLimit: value } })} /><NumberField id="silence-timeout" label={t("forms.silenceTimeout")} min={1} disabled={readOnly} value={template.content.interactionLimits.silenceTimeoutSeconds} onChange={(value) => setContent({ ...template.content, interactionLimits: { ...template.content.interactionLimits, silenceTimeoutSeconds: value } })} /><NumberField id="silence-prompts" label={t("forms.silencePrompts")} disabled={readOnly} value={template.content.interactionLimits.silencePromptLimit} onChange={(value) => setContent({ ...template.content, interactionLimits: { ...template.content.interactionLimits, silencePromptLimit: value } })} /></div>
    </CardContent></Card>

    <div className="space-y-4">{template.content.sections.map((section, sectionIndex) => <Card key={section.sectionId}><CardHeader><div className="flex flex-wrap items-center justify-between gap-2"><CardTitle>{t("forms.sectionNumber", { number: sectionIndex + 1 })}</CardTitle>{!readOnly && <div className="flex gap-1"><Button size="icon-sm" variant="ghost" aria-label={t("forms.moveUp")} disabled={sectionIndex === 0} onClick={() => moveSection(sectionIndex, -1)}><ArrowUp /></Button><Button size="icon-sm" variant="ghost" aria-label={t("forms.moveDown")} disabled={sectionIndex === template.content.sections.length - 1} onClick={() => moveSection(sectionIndex, 1)}><ArrowDown /></Button><Button size="icon-sm" variant="ghost" aria-label={t("forms.removeSection")} disabled={template.content.sections.length === 1} onClick={() => setContent({ ...template.content, sections: template.content.sections.filter((_, index) => index !== sectionIndex) })}><Trash2 /></Button></div>}</div></CardHeader><CardContent className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3"><div className="space-y-2"><Label htmlFor={`kind-${section.sectionId}`}>{t("forms.sectionKind")}</Label><select id={`kind-${section.sectionId}`} disabled={readOnly || template.locale === "en-US"} className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm disabled:opacity-60" value={section.kind} onChange={(event) => { const kind = event.target.value; updateSection(sectionIndex, { ...section, kind, languageTag: kind === "ENGLISH_SCREEN" ? "en-US" : template.locale }); }}><option value="CORE">CORE</option>{template.locale === "vi-VN" && <option value="ENGLISH_SCREEN">ENGLISH SCREEN</option>}</select></div><div className="space-y-2"><Label htmlFor={`section-language-${section.sectionId}`}>{t("fields.language")}</Label><Input id={`section-language-${section.sectionId}`} disabled value={section.languageTag} /></div><NumberField id={`section-duration-${section.sectionId}`} label={t("forms.sectionDuration")} min={1} max={section.kind === "ENGLISH_SCREEN" ? 300 : undefined} disabled={readOnly} value={section.durationLimitSeconds} onChange={(value) => updateSection(sectionIndex, { ...section, durationLimitSeconds: value })} /></div>
        <div className="space-y-2"><Label htmlFor={`transition-${section.sectionId}`}>{t("forms.transitionText")}</Label><textarea id={`transition-${section.sectionId}`} disabled={readOnly} className="min-h-16 w-full rounded-lg border border-input bg-background p-3 text-sm disabled:opacity-60" value={section.transitionText ?? ""} onChange={(event) => updateSection(sectionIndex, { ...section, transitionText: event.target.value || null })} /></div>
        <div className="space-y-3">{section.questions.map((question, questionIndex) => <fieldset key={question.questionId} disabled={readOnly} className="space-y-3 rounded-lg border p-4"><legend className="px-1 text-sm font-semibold">{t("forms.questionNumber", { number: questionIndex + 1 })}</legend><div className="flex justify-end gap-1"><Button size="icon-sm" variant="ghost" aria-label={t("forms.moveUp")} disabled={questionIndex === 0} onClick={() => moveQuestion(sectionIndex, questionIndex, -1)}><ArrowUp /></Button><Button size="icon-sm" variant="ghost" aria-label={t("forms.moveDown")} disabled={questionIndex === section.questions.length - 1} onClick={() => moveQuestion(sectionIndex, questionIndex, 1)}><ArrowDown /></Button><Button size="icon-sm" variant="ghost" aria-label={t("forms.removeQuestion")} disabled={section.questions.length === 1} onClick={() => updateSection(sectionIndex, { ...section, questions: section.questions.filter((_, index) => index !== questionIndex) })}><Trash2 /></Button></div>
          <div className="space-y-2"><Label htmlFor={`prompt-${question.questionId}`}>{t("forms.questionPrompt")}</Label><textarea id={`prompt-${question.questionId}`} className="min-h-20 w-full rounded-lg border border-input bg-background p-3 text-sm" value={question.prompt} onChange={(event) => updateQuestion(sectionIndex, questionIndex, { ...question, prompt: event.target.value })} /></div>
          <div className="grid gap-4 md:grid-cols-2"><div className="space-y-2"><Label htmlFor={`competency-${question.questionId}`}>{t("forms.competency")}</Label><Input id={`competency-${question.questionId}`} value={question.competency} onChange={(event) => updateQuestion(sectionIndex, questionIndex, { ...question, competency: event.target.value })} /></div><NumberField id={`follow-up-${question.questionId}`} label={t("forms.followUpLimit")} disabled={readOnly} value={question.followUpLimit} onChange={(value) => updateQuestion(sectionIndex, questionIndex, { ...question, followUpLimit: value })} /></div>
          <div className="space-y-2"><Label htmlFor={`rubric-${question.questionId}`}>{t("forms.rubric")}</Label><textarea id={`rubric-${question.questionId}`} className="min-h-20 w-full rounded-lg border border-input bg-background p-3 text-sm" value={question.rubric} onChange={(event) => updateQuestion(sectionIndex, questionIndex, { ...question, rubric: event.target.value })} /></div>
        </fieldset>)}{!readOnly && <Button variant="outline" onClick={() => updateSection(sectionIndex, { ...section, questions: [...section.questions, newQuestion()] })}><Plus />{t("forms.addQuestion")}</Button>}</div>
      </CardContent></Card>)}{!readOnly && <Button variant="outline" onClick={() => setContent({ ...template.content, sections: [...template.content.sections, newSection(template.locale)] })}><Plus />{t("forms.addSection")}</Button>}</div>

    {!record?.archived && (!record || editingContent) && <div className="flex justify-end gap-2"><Button variant="outline" nativeButton={false} render={<Link href="/recruitment/templates" />}>{t("actions.cancel")}</Button><Button disabled={saving} onClick={() => void save()}>{saving ? t("actions.saving") : record ? t("forms.saveRevision") : t("save")}</Button></div>}
    {record && !record.archived && !editingContent && <div className="flex justify-end"><Button disabled={saving} onClick={() => void save()}>{saving ? t("actions.saving") : t("forms.saveMetadata")}</Button></div>}
  </div>;
}

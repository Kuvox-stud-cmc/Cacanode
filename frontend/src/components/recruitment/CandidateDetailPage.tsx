"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  deleteRecruitmentCandidate,
  getRecruitmentCandidate,
  listRecruitmentApplications,
  saveCandidate,
  type RecruitmentApplication,
  type RecruitmentCandidate,
} from "@/lib/recruitment-admin-api";
import { ArrowLeft, Edit3, Trash2, User, FileText, Calendar, Plus } from "lucide-react";
import { useLocaleChangeDraft } from "@/hooks/useLocaleChangeDraft";
import { useRecruitmentConfirmation } from "@/components/recruitment/useRecruitmentConfirmation";
import { formatEnumLabel } from "@/lib/recruitment-formatters";

export function CandidateDetailPage({ candidateId }: { candidateId: string }) {
  const t = useTranslations("Recruitment");
  const c = useTranslations("Recruitment.candidatePages");
  const locale = useLocale();
  const format = useFormatter();
  const { request } = useApiClient();
  const router = useRouter();
  const { confirm, confirmationDialog } = useRecruitmentConfirmation();

  const [candidate, setCandidate] = useState<RecruitmentCandidate | null>(null);
  const [applications, setApplications] = useState<RecruitmentApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ fullName: "", email: "", phone: "", notes: "" });
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [candRes, appsRes] = await Promise.all([
        getRecruitmentCandidate(request, candidateId),
        listRecruitmentApplications(request, { candidateId, page: 0, size: 100 }),
      ]);
      setCandidate(candRes);
      setForm({
        fullName: candRes.fullName,
        email: candRes.email,
        phone: candRes.phone || "",
        notes: candRes.notes || "",
      });
      setApplications(appsRes.items);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [candidateId, request, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const clearLocaleDraft = useLocaleChangeDraft(
    `recruitment:candidate:${candidateId}`,
    { editing, form },
    (draft) => {
      setEditing(draft.editing);
      setForm(draft.form);
    },
    !loading && Boolean(candidate),
  );

  const handleSave = async () => {
    setSaving(true);
    setError("");
    try {
      const updated = await saveCandidate(request, {
        id: candidateId,
        fullName: form.fullName,
        email: form.email,
        phone: form.phone || null,
        notes: form.notes || null,
      });
      clearLocaleDraft();
      setCandidate(updated);
      setEditing(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("forms.createError"));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!candidate || !await confirm({ title: t("dialogs.deleteCandidateTitle", { name: candidate.fullName }), description: t("dialogs.deleteCandidate"), confirmLabel: t("forms.delete"), destructive: true })) return;
    try {
      await deleteRecruitmentCandidate(request, candidateId);
      clearLocaleDraft();
      router.push("/recruitment/candidates");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    }
  };

  if (loading) return <p className="p-6 text-sm text-muted-foreground">{t("loading")}</p>;
  if (!candidate) return <p className="p-6 text-sm text-red-600">{error || t("loadError")}</p>;

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b pb-4">
        <div>
          <Button variant="ghost" size="sm" nativeButton={false} render={<Link href="/recruitment/candidates" />}>
            <ArrowLeft className="mr-1 h-4 w-4" /> {c("back")}
          </Button>
          <h2 className="text-2xl font-bold mt-2">{candidate.fullName}</h2>
          <p className="text-sm text-muted-foreground">{c("candidateId")}: {candidate.id}</p>
        </div>

        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => {
            if (editing && candidate) {
              clearLocaleDraft();
              setForm({ fullName: candidate.fullName, email: candidate.email, phone: candidate.phone || "", notes: candidate.notes || "" });
            }
            setEditing(!editing);
          }}>
            <Edit3 className="mr-1 h-4 w-4" /> {editing ? c("cancel") : c("editProfile")}
          </Button>
          <Button variant="destructive" size="sm" onClick={() => void handleDelete()}>
            <Trash2 className="mr-1 h-4 w-4" /> {c("delete")}
          </Button>
        </div>
      </div>

      {error && <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <div className="grid gap-6 md:grid-cols-3">
        {/* Left column: Profile info / Edit Form */}
        <Card className="md:col-span-1">
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <User className="h-4 w-4 text-indigo-600" /> {c("candidateInfo")}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {editing ? (
              <div className="space-y-3">
                <div className="space-y-1">
                  <label className="text-xs font-medium">{c("fullName")}</label>
                  <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium">{c("email")}</label>
                  <Input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium">{c("phone")}</label>
                  <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium">{c("notes")}</label>
                  <textarea
                    className="w-full min-h-20 rounded-md border p-2 text-sm"
                    value={form.notes}
                    onChange={(e) => setForm({ ...form, notes: e.target.value })}
                  />
                </div>
                <Button size="sm" className="w-full" disabled={saving} onClick={() => void handleSave()}>
                  {saving ? t("actions.saving") : t("save")}
                </Button>
              </div>
            ) : (
              <div className="space-y-3 text-sm">
                <div>
                  <p className="text-xs text-muted-foreground">{c("emailAddress")}</p>
                  <p className="font-medium">{candidate.email}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{c("phoneNumber")}</p>
                  <p className="font-medium">{candidate.phone || "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">{c("createdAt")}</p>
                  <p>{format.dateTime(new Date(candidate.createdAt), { dateStyle: "medium" })}</p>
                </div>
                {candidate.notes && (
                  <div>
                    <p className="text-xs text-muted-foreground">{c("recruiterNotes")}</p>
                    <p className="text-xs bg-slate-50 p-2 rounded border">{candidate.notes}</p>
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Right column: Application History */}
        <Card className="md:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-base flex items-center gap-2">
              <FileText className="h-4 w-4 text-indigo-600" /> {c("applicationHistory", { count: applications.length })}
            </CardTitle>
            <Button size="sm" variant="outline" nativeButton={false} render={<Link href={`/recruitment/applications?candidateId=${candidateId}`} />}>
              <Plus className="mr-1 h-3.5 w-3.5" /> {c("viewApplications")}
            </Button>
          </CardHeader>
          <CardContent>
            {applications.length === 0 ? (
              <p className="text-sm text-muted-foreground py-4">{c("noApplications")}</p>
            ) : (
              <div className="divide-y border rounded-md">
                {applications.map((app) => (
                  <div key={app.id} className="p-4 flex items-center justify-between hover:bg-slate-50">
                    <div>
                      <strong className="text-sm font-semibold">{app.jobTitle}</strong>
                      <div className="flex items-center gap-2 mt-1">
                        <Badge variant="outline">{formatEnumLabel(app.status, locale)}</Badge>
                        {app.submittedAt && (
                          <span className="text-xs text-muted-foreground">
                            <Calendar className="inline h-3 w-3 mr-1" />
                            {format.dateTime(new Date(app.submittedAt), { dateStyle: "short" })}
                          </span>
                        )}
                      </div>
                    </div>
                    <Button size="sm" variant="ghost" nativeButton={false} render={<Link href={`/recruitment/applications/${app.id}`} />}>
                      {c("view")}
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
      {confirmationDialog}
    </div>
  );
}

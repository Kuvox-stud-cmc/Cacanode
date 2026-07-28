"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  deleteRecruitmentCandidate,
  listRecruitmentCandidates,
  saveCandidate,
  type RecruitmentCandidate,
} from "@/lib/recruitment-admin-api";
import { Plus, Eye, Trash2, Edit3, UserCheck } from "lucide-react";
import { useAuthStore } from "@/components/providers/StoreProvider";
import { useLocaleChangeDraft } from "@/hooks/useLocaleChangeDraft";
import { useRecruitmentConfirmation } from "@/components/recruitment/useRecruitmentConfirmation";

export function CandidatesListPage() {
  const t = useTranslations("Recruitment");
  const c = useTranslations("Recruitment.candidatePages");
  const format = useFormatter();
  const { request } = useApiClient();
  const search = useSearchParams();
  const router = useRouter();
  const tenantId = useAuthStore((state) => state.user?.tenantId);
  const { confirm, confirmationDialog } = useRecruitmentConfirmation();

  const page = Number(search.get("page") ?? 0);
  const q = search.get("q") ?? "";

  const [query, setQuery] = useState(q);
  const [rows, setRows] = useState<RecruitmentCandidate[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [modalOpen, setModalOpen] = useState(false);
  const [editingCandidate, setEditingCandidate] = useState<{ id?: string; fullName: string; email: string; phone: string; notes: string }>({
    fullName: "",
    email: "",
    phone: "",
    notes: "",
  });
  const [saving, setSaving] = useState(false);
  const clearLocaleDraft = useLocaleChangeDraft(
    `recruitment:candidate-dialog:${tenantId ?? "unknown"}`,
    { modalOpen, editingCandidate },
    (draft) => {
      setModalOpen(draft.modalOpen);
      setEditingCandidate(draft.editingCandidate);
    },
    Boolean(tenantId),
  );

  const updateParams = useCallback((values: Record<string, string | null>) => {
    const next = new URLSearchParams(search.toString());
    Object.entries(values).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    router.replace(`/recruitment/candidates?${next}`);
  }, [router, search]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (query !== q) updateParams({ q: query, page: null });
    }, 300);
    return () => window.clearTimeout(timer);
  }, [q, query, updateParams]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await listRecruitmentCandidates(request, { page, size: 20, q: q || undefined });
      setRows(result.items);
      setTotal(result.total);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [page, q, request, t]);

  useEffect(() => {
    // Client-side filters trigger an intentional loading-state refresh.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const handleSave = async () => {
    if (!editingCandidate.fullName || !editingCandidate.email) return;
    setSaving(true);
    setError("");
    try {
      await saveCandidate(request, {
        id: editingCandidate.id,
        fullName: editingCandidate.fullName,
        email: editingCandidate.email,
        phone: editingCandidate.phone || null,
        notes: editingCandidate.notes || null,
      });
      clearLocaleDraft();
      setModalOpen(false);
      setEditingCandidate({ fullName: "", email: "", phone: "", notes: "" });
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("forms.createError"));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (cand: RecruitmentCandidate) => {
    if (!await confirm({ title: t("dialogs.deleteCandidateTitle", { name: cand.fullName }), description: t("dialogs.deleteCandidate"), confirmLabel: t("forms.delete"), destructive: true })) return;
    try {
      await deleteRecruitmentCandidate(request, cand.id);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    }
  };

  const openCreateModal = () => {
    clearLocaleDraft();
    setEditingCandidate({ fullName: "", email: "", phone: "", notes: "" });
    setModalOpen(true);
  };

  const openEditModal = (cand: RecruitmentCandidate) => {
    clearLocaleDraft();
    setEditingCandidate({
      id: cand.id,
      fullName: cand.fullName,
      email: cand.email,
      phone: cand.phone || "",
      notes: cand.notes || "",
    });
    setModalOpen(true);
  };

  const pages = Math.max(1, Math.ceil(total / 20));

  return (
    <div className="space-y-4" aria-live="polite">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">{t("nav.candidates")}</h3>
          <p className="text-sm text-muted-foreground">{t("pages.candidates")}</p>
        </div>
        <Button onClick={openCreateModal}>
          <Plus className="mr-1 h-4 w-4" />
          {t("actions.createCandidate")}
        </Button>
      </div>

      <div className="flex flex-wrap gap-2">
        <Input
          aria-label={t("search")}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t("search")}
          className="max-w-sm"
        />
      </div>

      {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <Card>
        <CardContent className="p-0">
          <div className="divide-y">
            {rows.map((cand) => (
              <div key={cand.id} className="flex flex-wrap items-center justify-between gap-3 p-4 hover:bg-slate-50">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <UserCheck className="h-4 w-4 text-indigo-600" />
                    <strong className="truncate text-base font-medium">{cand.fullName}</strong>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {c("email")}: <span className="font-medium text-foreground">{cand.email}</span> · {c("phone")}: {cand.phone || "—"}
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {c("added")}: {format.dateTime(new Date(cand.createdAt), { dateStyle: "short" })}
                    {cand.notes && ` · ${c("notes")}: ${cand.notes}`}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Button size="sm" variant="ghost" aria-label={c("editCandidate", { name: cand.fullName })} onClick={() => openEditModal(cand)}>
                    <Edit3 className="h-4 w-4" />
                  </Button>
                  <Button size="sm" variant="ghost" aria-label={c("deleteCandidate", { name: cand.fullName })} className="text-red-600 hover:bg-red-50" onClick={() => void handleDelete(cand)}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                  <Button size="sm" variant="outline" nativeButton={false} render={<Link href={`/recruitment/candidates/${cand.id}`} />}>
                    <Eye className="mr-1 h-3.5 w-3.5" /> {c("details")}
                  </Button>
                </div>
              </div>
            ))}
            {loading && <p className="p-8 text-center text-sm text-muted-foreground">{t("loading")}</p>}
            {!loading && rows.length === 0 && <p className="p-8 text-center text-sm text-muted-foreground">{t("empty")}</p>}
          </div>
        </CardContent>
      </Card>

      <div className="flex items-center justify-between">
        <span className="text-sm text-muted-foreground">
          {t("pagination", { page: page + 1, pages, total })}
        </span>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => updateParams({ page: String(page - 1) })}>
            {t("previous")}
          </Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => updateParams({ page: String(page + 1) })}>
            {t("next")}
          </Button>
        </div>
      </div>

      {/* Candidate Create/Edit Modal */}
      <Dialog open={modalOpen} onOpenChange={(open) => { setModalOpen(open); if (!open) clearLocaleDraft(); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingCandidate.id ? c("editCandidateTitle") : t("actions.createCandidate")}</DialogTitle>
            <DialogDescription>{editingCandidate.id ? c("editCandidateDescription") : t("candidateDialog")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <Input
              placeholder={t("fields.fullName")}
              value={editingCandidate.fullName}
              onChange={(e) => setEditingCandidate({ ...editingCandidate, fullName: e.target.value })}
            />
            <Input
              placeholder={t("fields.email")}
              type="email"
              value={editingCandidate.email}
              onChange={(e) => setEditingCandidate({ ...editingCandidate, email: e.target.value })}
            />
            <Input
              placeholder={t("fields.phone")}
              value={editingCandidate.phone}
              onChange={(e) => setEditingCandidate({ ...editingCandidate, phone: e.target.value })}
            />
            <textarea
              className="w-full min-h-20 rounded-md border border-input p-2 text-sm"
              placeholder={t("fields.notes")}
              value={editingCandidate.notes}
              onChange={(e) => setEditingCandidate({ ...editingCandidate, notes: e.target.value })}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { clearLocaleDraft(); setModalOpen(false); }}>
              {c("cancel")}
            </Button>
            <Button onClick={() => void handleSave()} disabled={!editingCandidate.fullName || !editingCandidate.email || saving}>
              {saving ? t("actions.saving") : t("save")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {confirmationDialog}
    </div>
  );
}

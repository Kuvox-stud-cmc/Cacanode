"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  createRecruitmentApplication,
  listRecruitmentApplications,
  listRecruitmentCandidates,
  listRecruitmentJobs,
  sendApplicationCompletionLink,
  type RecruitmentApplication,
  type RecruitmentCandidate,
  type RecruitmentJob,
} from "@/lib/recruitment-admin-api";
import { Plus, Send, Eye, FileText } from "lucide-react";

import { formatEnumLabel } from "@/lib/recruitment-formatters";

const applicationStatuses = [
  "AWAITING_CANDIDATE",
  "SUBMITTED",
  "INTERVIEW_INVITED",
  "INTERVIEW_SCHEDULED",
  "INTERVIEW_COMPLETED",
  "UNDER_REVIEW",
  "SHORTLISTED",
  "REJECTED",
  "WITHDRAWN",
];

export function ApplicationsListPage() {
  const t = useTranslations("Recruitment");
  const locale = useLocale();
  const format = useFormatter();
  const { request } = useApiClient();
  const search = useSearchParams();
  const router = useRouter();

  const page = Number(search.get("page") ?? 0);
  const q = search.get("q") ?? "";
  const filterStatus = search.get("status") ?? "";

  const [query, setQuery] = useState(q);
  const [rows, setRows] = useState<RecruitmentApplication[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionSuccess, setActionSuccess] = useState("");

  const [createOpen, setCreateOpen] = useState(false);
  const [candidates, setCandidates] = useState<RecruitmentCandidate[]>([]);
  const [jobs, setJobs] = useState<RecruitmentJob[]>([]);
  const [selectedCandidateId, setSelectedCandidateId] = useState("");
  const [selectedJobId, setSelectedJobId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [cooldown, setCooldown] = useState<Record<string, number>>({});

  const updateParams = useCallback((values: Record<string, string | null>) => {
    const next = new URLSearchParams(search.toString());
    Object.entries(values).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    router.replace(`/recruitment/applications?${next}`);
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
      const result = await listRecruitmentApplications(request, {
        page,
        size: 20,
        q: q || undefined,
        status: filterStatus || undefined,
      });
      setRows(result.items);
      setTotal(result.total);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, [filterStatus, page, q, request, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreateModal = async () => {
    try {
      const [candRes, jobRes] = await Promise.all([
        listRecruitmentCandidates(request, { page: 0, size: 100 }),
        listRecruitmentJobs(request, { page: 0, size: 100, status: "PUBLISHED" }),
      ]);
      setCandidates(candRes.items);
      setJobs(jobRes.items);
      setCreateOpen(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    }
  };

  const handleCreate = async () => {
    if (!selectedJobId || !selectedCandidateId) return;
    setSubmitting(true);
    setError("");
    try {
      await createRecruitmentApplication(request, { jobId: selectedJobId, candidateId: selectedCandidateId });
      setCreateOpen(false);
      setSelectedJobId("");
      setSelectedCandidateId("");
      setActionSuccess("Application created. Completion link ready to send.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("forms.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleResendCompletionLink = async (appId: string) => {
    if (cooldown[appId] && Date.now() < cooldown[appId]) return;
    try {
      await sendApplicationCompletionLink(request, appId);
      setCooldown((prev) => ({ ...prev, [appId]: Date.now() + 60000 }));
      setActionSuccess("Completion link sent successfully.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    }
  };

  const pages = Math.max(1, Math.ceil(total / 20));

  return (
    <div className="space-y-4" aria-live="polite">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">{t("nav.applications")}</h3>
          <p className="text-sm text-muted-foreground">{t("pages.applications")}</p>
        </div>
        <Button onClick={() => void openCreateModal()}>
          <Plus className="mr-1 h-4 w-4" />
          Create Application
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
        <Select
          value={filterStatus || "ALL"}
          onValueChange={(val) => updateParams({ status: val === "ALL" ? null : val, page: null })}
        >
          <SelectTrigger className="w-52">
            <SelectValue>{filterStatus ? formatEnumLabel(filterStatus, locale) : t("allStatuses")}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t("allStatuses")}</SelectItem>
            {applicationStatuses.map((st) => (
              <SelectItem key={st} value={st}>
                {formatEnumLabel(st, locale)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {actionSuccess && <p role="status" className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">{actionSuccess}</p>}

      <Card>
        <CardContent className="p-0">
          <div className="divide-y">
            {rows.map((app) => (
              <div key={app.id} className="flex flex-wrap items-center justify-between gap-3 p-4 hover:bg-slate-50">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <strong className="truncate text-base font-medium">{app.candidateName}</strong>
                    <Badge variant="outline">{formatEnumLabel(app.status, locale)}</Badge>
                    {app.cvPresent && (
                      <Badge variant="secondary" className="text-xs">
                        <FileText className="mr-1 h-3 w-3" /> CV Attached
                      </Badge>
                    )}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Job: <span className="font-medium text-foreground">{app.jobTitle}</span> · Email: {app.candidateEmail}
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Submitted: {app.submittedAt ? format.dateTime(new Date(app.submittedAt), { dateStyle: "short", timeStyle: "short" }) : "Pending candidate"}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {app.status === "AWAITING_CANDIDATE" && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={Boolean(cooldown[app.id] && Date.now() < cooldown[app.id])}
                      onClick={() => void handleResendCompletionLink(app.id)}
                    >
                      <Send className="mr-1 h-3.5 w-3.5" />
                      {cooldown[app.id] && Date.now() < cooldown[app.id] ? "Resent (60s)" : "Send Link"}
                    </Button>
                  )}
                  <Button size="sm" variant="outline" nativeButton={false} render={<Link href={`/recruitment/applications/${app.id}`} />}>
                    <Eye className="mr-1 h-3.5 w-3.5" /> View Detail
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

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Candidate Application</DialogTitle>
            <DialogDescription>
              Select an existing candidate and a published job posting to create an application awaiting candidate completion.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <label className="text-sm font-medium">Candidate *</label>
              <select
                className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm"
                value={selectedCandidateId}
                onChange={(e) => setSelectedCandidateId(e.target.value)}
              >
                <option value="">Select Candidate...</option>
                {candidates.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.fullName} ({c.email})
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Job Posting *</label>
              <select
                className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm"
                value={selectedJobId}
                onChange={(e) => setSelectedJobId(e.target.value)}
              >
                <option value="">Select Job Posting...</option>
                {jobs.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.title} ({j.department || "General"})
                  </option>
                ))}
              </select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => void handleCreate()} disabled={!selectedCandidateId || !selectedJobId || submitting}>
              {submitting ? "Creating..." : "Create Application"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

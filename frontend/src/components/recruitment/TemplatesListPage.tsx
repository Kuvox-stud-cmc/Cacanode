"use client";

import { useCallback, useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, useRouter } from "@/i18n/navigation";
import { useApiClient } from "@/hooks/useApiClient";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  archiveRecruitmentTemplate,
  listRecruitmentTemplates,
  type RecruitmentTemplate,
} from "@/lib/recruitment-admin-api";
import { Plus, Eye, Archive, FileCode } from "lucide-react";
import { useRecruitmentConfirmation } from "@/components/recruitment/useRecruitmentConfirmation";

export function TemplatesListPage() {
  const t = useTranslations("Recruitment");
  const format = useFormatter();
  const { request } = useApiClient();
  const search = useSearchParams();
  const router = useRouter();
  const { confirm, confirmationDialog } = useRecruitmentConfirmation();

  const page = Number(search.get("page") ?? 0);
  const q = search.get("q") ?? "";

  const [query, setQuery] = useState(q);
  const [rows, setRows] = useState<RecruitmentTemplate[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const updateParams = useCallback((values: Record<string, string | null>) => {
    const next = new URLSearchParams(search.toString());
    Object.entries(values).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    router.replace(`/recruitment/templates?${next}`);
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
      const result = await listRecruitmentTemplates(request, { page, size: 20, q: q || undefined });
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

  const handleArchive = async (tmpl: RecruitmentTemplate) => {
    if (!await confirm({ title: `${t("forms.archiveTemplate")}: ${tmpl.name}`, description: t("forms.confirm.archiveTemplate"), confirmLabel: t("actions.archive"), destructive: true })) return;
    try {
      await archiveRecruitmentTemplate(request, tmpl.id);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"));
    }
  };

  const pages = Math.max(1, Math.ceil(total / 20));

  return (
    <div className="space-y-4" aria-live="polite">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">{t("nav.templates")}</h3>
          <p className="text-sm text-muted-foreground">{t("pages.templates")}</p>
        </div>
        <Button nativeButton={false} render={<Link href="/recruitment/templates/new" />}>
          <Plus className="mr-1 h-4 w-4" />
          {t("actions.createTemplate")}
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
            {rows.map((tmpl) => (
              <div key={tmpl.id} className="flex flex-wrap items-center justify-between gap-3 p-4 hover:bg-slate-50">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <FileCode className="h-4 w-4 text-indigo-600" />
                    <strong className="truncate text-base font-medium">{tmpl.name}</strong>
                    <Badge variant={tmpl.archived ? "destructive" : "outline"}>
                      {tmpl.archived ? t("forms.archived") : `v${tmpl.latestRevisionNumber}`}
                    </Badge>
                    <Badge variant="secondary" className="text-xs">
                      {tmpl.locale === "vi-VN" ? t("forms.viVN") : t("forms.enUS")}
                    </Badge>
                  </div>
                  {tmpl.description && <p className="text-sm text-muted-foreground truncate">{tmpl.description}</p>}
                  <p className="text-xs text-slate-400 mt-0.5">
                    {t("forms.updatedAt", { date: format.dateTime(new Date(tmpl.updatedAt), { dateStyle: "short" }) })}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {!tmpl.archived && (
                    <Button size="sm" variant="ghost" className="text-red-600 hover:bg-red-50" aria-label={t("forms.archiveTemplate")} title={t("forms.archiveTemplate")} onClick={() => void handleArchive(tmpl)}>
                      <Archive className="h-4 w-4" />
                    </Button>
                  )}
                  <Button size="sm" variant="outline" nativeButton={false} render={<Link href={`/recruitment/templates/${tmpl.id}`} />}>
                    <Eye className="mr-1 h-3.5 w-3.5" /> {t("actions.editRevisions")}
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
      {confirmationDialog}
    </div>
  );
}

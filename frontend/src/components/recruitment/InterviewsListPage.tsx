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
import {
  listRecruitmentInterviews,
  type RecruitmentInterview,
} from "@/lib/recruitment-admin-api";
import { Eye, Mic } from "lucide-react";

import { formatEnumLabel, formatTimezoneLabel } from "@/lib/recruitment-formatters";

const interviewStatuses = [
  "INVITED",
  "SCHEDULED",
  "IN_PROGRESS",
  "COMPLETED",
  "FAILED",
  "NO_ANSWER",
  "DECLINED",
  "CANCELLED",
  "EXPIRED",
];

export function InterviewsListPage() {
  const t = useTranslations("Recruitment");
  const i = useTranslations("Recruitment.interviewPages");
  const locale = useLocale();
  const format = useFormatter();
  const { request } = useApiClient();
  const search = useSearchParams();
  const router = useRouter();

  const page = Number(search.get("page") ?? 0);
  const q = search.get("q") ?? "";
  const filterStatus = search.get("status") ?? "";

  const [query, setQuery] = useState(q);
  const [rows, setRows] = useState<RecruitmentInterview[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const updateParams = useCallback((values: Record<string, string | null>) => {
    const next = new URLSearchParams(search.toString());
    Object.entries(values).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    router.replace(`/recruitment/interviews?${next}`);
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
      const result = await listRecruitmentInterviews(request, {
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
    // Client-side filters trigger an intentional loading-state refresh.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const pages = Math.max(1, Math.ceil(total / 20));

  return (
    <div className="space-y-4" aria-live="polite">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">{t("nav.interviews")}</h3>
          <p className="text-sm text-muted-foreground">{t("pages.interviews")}</p>
        </div>
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
            {interviewStatuses.map((st) => (
              <SelectItem key={st} value={st}>
                {formatEnumLabel(st, locale)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <Card>
        <CardContent className="p-0">
          <div className="divide-y">
            {rows.map((inv) => (
              <div key={inv.id} className="flex flex-wrap items-center justify-between gap-3 p-4 hover:bg-slate-50">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <Mic className="h-4 w-4 text-indigo-600" />
                    <strong className="truncate text-base font-medium">{inv.candidateName}</strong>
                    <Badge variant="outline">{formatEnumLabel(inv.status, locale)}</Badge>
                    {inv.overallScore !== null && inv.overallScore !== undefined && (
                      <Badge variant="secondary" className="text-xs font-semibold bg-indigo-50 text-indigo-700 border-indigo-200">
                        {i("score")}: {inv.overallScore}/100
                      </Badge>
                    )}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {i("job")}: <span className="font-medium text-foreground">{inv.jobTitle}</span>
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {inv.scheduledStartAt ? (
                      <>{i("scheduled")}: {format.dateTime(new Date(inv.scheduledStartAt), { dateStyle: "short", timeStyle: "short" })} ({formatTimezoneLabel(inv.schedulingTimezone)})</>
                    ) : (
                      i("notScheduled")
                    )}
                    {inv.rescheduleCount > 0 && ` · ${i("rescheduled", { count: inv.rescheduleCount })}`}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Button size="sm" variant="outline" nativeButton={false} render={<Link href={`/recruitment/interviews/${inv.id}`} />}>
                    <Eye className="mr-1 h-3.5 w-3.5" /> {i("detailsAudio")}
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
    </div>
  );
}

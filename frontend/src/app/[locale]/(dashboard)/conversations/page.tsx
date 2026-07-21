"use client";


import { useEffect, useMemo, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { ChevronRight, Eye, Loader2, MessageSquare } from "lucide-react";
import toast from "react-hot-toast";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ClearFiltersButton, DateRangeFields, FilterPanel, FilterSelect, PaginationControls, ResultsSummary, UrlSearchField } from "@/components/list/ListControls";
import { useApiClient } from "@/hooks/useApiClient";
import { oneOf, safeDate, safePage, safeSize, useUrlListState } from "@/hooks/useUrlListState";
import { closeCustomerConversation, getCustomerConversation, listCustomerConversations, type CustomerConversation, type CustomerConversationDetail } from "@/lib/conversations-api";
import { cn } from "@/lib/utils";

const STATUS_VALUES = ["all", "OPEN", "CLOSED"] as const;
const SORT_VALUES = ["started-desc", "started-asc", "activity-desc", "customer-asc"] as const;

export default function ConversationsPage() {
  const t = useTranslations("Conversations");
  const format = useFormatter();
  const { request } = useApiClient();
  const { searchParams, update, clear } = useUrlListState();
  const status = oneOf(searchParams.get("status"), STATUS_VALUES, "all");
  const channel = oneOf(searchParams.get("channel"), ["all", "WIDGET", "CUSTOM_API"] as const, "all");
  const sortValue = oneOf(searchParams.get("sort"), SORT_VALUES, "started-desc");
  const page = safePage(searchParams.get("page"));
  const size = safeSize(searchParams.get("size"));
  const startedFrom = safeDate(searchParams.get("from"));
  const rawStartedTo = safeDate(searchParams.get("to"));
  const startedTo = rawStartedTo && (!startedFrom || rawStartedTo >= startedFrom) ? rawStartedTo : "";
  const urlQuery = (searchParams.get("q") ?? "").slice(0, 200);
  const [items, setItems] = useState<CustomerConversation[]>([]);
  const [total, setTotal] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const [loadedKey, setLoadedKey] = useState("");
  const [selected, setSelected] = useState<CustomerConversationDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [revision, setRevision] = useState(0);
  const customerLabel = (item: CustomerConversation) => item.customer_name || item.customer_email || item.external_user_id || t("anonymous");
  const channelLabel = (value: CustomerConversation["channel"]) => value === "WIDGET" ? t("widget") : t("api");
  const formatDate = (value: string) => format.dateTime(new Date(value), { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });

  const [sort, direction] = sortValue.split("-") as [string, "asc" | "desc"];
  const requestKey = [urlQuery, status, channel, startedFrom, startedTo, sortValue, page, size, revision].join("|");
  const refreshing = loaded && loadedKey !== requestKey;
  const hasFilters = Boolean(urlQuery || status !== "all" || channel !== "all" || startedFrom || startedTo || sortValue !== "started-desc");

  useEffect(() => {
    const controller = new AbortController();
    listCustomerConversations(request, {
      status: status === "all" ? undefined : status,
      channel: channel === "all" ? undefined : channel,
      q: urlQuery || undefined,
      startedFrom: startedFrom || undefined,
      startedTo: startedTo || undefined,
      sort,
      direction,
      offset: (page - 1) * size,
      limit: size,
      signal: controller.signal,
    }).then((result) => {
      if (controller.signal.aborted) return;
      const pages = Math.max(1, Math.ceil(result.total / size));
      if (page > pages) { update({ page: pages === 1 ? null : pages }, false); return; }
      setItems(result.items); setTotal(result.total); setLoaded(true); setLoadedKey(requestKey);
    }).catch((error) => {
      if (!(error instanceof DOMException && error.name === "AbortError")) toast.error(error instanceof Error ? error.message : t("loadError"));
    });
    return () => controller.abort();
  }, [channel, direction, page, request, requestKey, size, sort, startedFrom, startedTo, status, t, update, urlQuery]);

  async function openConversation(id: string) {
    setDetailLoading(true);
    try { setSelected(await getCustomerConversation(request, id)); }
    catch (error) { toast.error(error instanceof Error ? error.message : t("detailError")); }
    finally { setDetailLoading(false); }
  }
  async function closeConversation() {
    if (!selected) return;
    try {
      await closeCustomerConversation(request, selected.id);
      setSelected({ ...selected, status: "CLOSED", closed_at: new Date().toISOString() });
      setRevision((value) => value + 1);
      toast.success(t("closedToast"));
    } catch (error) { toast.error(error instanceof Error ? error.message : t("closeError")); }
  }

  const filterTabs = useMemo(() => STATUS_VALUES, []);
  return (
    <div className="space-y-4 sm:space-y-6">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <h2 className="text-xl font-semibold text-slate-800">{t("title")}</h2>
        <p className="text-sm text-slate-500">{t("description")}</p>
      </div>

      <div className="grid w-full grid-cols-3 gap-1 rounded-lg bg-slate-200 p-1 sm:w-fit">
        {filterTabs.map((value) => <button key={value} type="button" onClick={() => update({ status: value === "all" ? null : value })} className={cn("min-h-9 rounded-md px-3 text-sm font-medium", status === value ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900")}>{value === "all" ? t("status.all") : value === "OPEN" ? t("status.open") : t("status.closed")}</button>)}
      </div>

      <FilterPanel>
        <UrlSearchField key={urlQuery} initialValue={urlQuery} onDebouncedChange={(value) => update({ q: value || null })} placeholder={t("searchPlaceholder")} />
        <FilterSelect label={t("channel")} value={channel} onChange={(value) => update({ channel: value === "all" ? null : value })}><option value="all">{t("allChannels")}</option><option value="WIDGET">{t("widget")}</option><option value="CUSTOM_API">{t("customApi")}</option></FilterSelect>
        <DateRangeFields prefix={t("started")} from={startedFrom} to={startedTo} onFromChange={(value) => update({ from: value || null })} onToChange={(value) => update({ to: value || null })} />
        <FilterSelect label={t("sort")} value={sortValue} onChange={(value) => update({ sort: value === "started-desc" ? null : value })}><option value="started-desc">{t("sortOptions.newest")}</option><option value="started-asc">{t("sortOptions.oldest")}</option><option value="activity-desc">{t("sortOptions.activity")}</option><option value="customer-asc">{t("sortOptions.customer")}</option></FilterSelect>
        <ClearFiltersButton disabled={!hasFilters} onClick={clear} />
      </FilterPanel>
      <ResultsSummary total={total} refreshing={refreshing} />

      <Card className="overflow-hidden">
        {!loaded ? <div className="grid min-h-56 place-items-center"><Loader2 className="animate-spin text-indigo-600" /></div> : items.length === 0 ? <div className="px-4 py-16 text-center"><MessageSquare className="mx-auto mb-3 size-9 text-slate-300" /><p className="text-sm font-medium text-slate-500">{hasFilters ? t("noMatches") : t("empty")}</p></div> : <>
          <div className="divide-y divide-slate-200 lg:hidden">{items.map((item) => <button key={item.id} type="button" onClick={() => void openConversation(item.id)} className="flex w-full items-start gap-3 p-4 text-left hover:bg-slate-50"><span className="grid size-10 shrink-0 place-items-center rounded-full bg-indigo-50 text-indigo-600"><MessageSquare className="size-4" /></span><span className="min-w-0 flex-1"><span className="flex justify-between gap-2"><span className="min-w-0"><span className="block truncate text-sm font-semibold">{customerLabel(item)}</span><span className="block truncate text-xs text-slate-500">{item.customer_email}</span></span><Badge variant="outline" className="shrink-0 text-[10px]">{item.status === "OPEN" ? t("status.open") : t("status.closed")}</Badge></span><span className="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">{channelLabel(item.channel)} · {t("messageCount", { count: item.message_count })} · {formatDate(item.created_at)}</span></span><ChevronRight className="mt-2 size-4 text-slate-400" /></button>)}</div>
          <div className="hidden lg:block"><Table><TableHeader><TableRow><TableHead>{t("table.customer")}</TableHead><TableHead>{t("table.channel")}</TableHead><TableHead>{t("table.messages")}</TableHead><TableHead>{t("table.started")}</TableHead><TableHead>{t("table.status")}</TableHead><TableHead className="w-10" /></TableRow></TableHeader><TableBody>{items.map((item) => <TableRow key={item.id} className="cursor-pointer" tabIndex={0} onClick={() => void openConversation(item.id)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); void openConversation(item.id); } }}><TableCell><p className="font-medium">{customerLabel(item)}</p><p className="max-w-64 truncate text-xs text-slate-500">{item.customer_email}</p></TableCell><TableCell><Badge variant="outline">{channelLabel(item.channel)}</Badge></TableCell><TableCell>{format.number(item.message_count)}</TableCell><TableCell>{formatDate(item.created_at)}</TableCell><TableCell><Badge variant="outline">{item.status === "OPEN" ? t("status.open") : t("status.closed")}</Badge></TableCell><TableCell><Eye className="size-4 text-slate-400" /></TableCell></TableRow>)}</TableBody></Table></div>
        </>}
        {loaded && <PaginationControls page={page} size={size} total={total} onPageChange={(value) => update({ page: value === 1 ? null : value }, false)} onSizeChange={(value) => update({ size: value === 20 ? null : value })} />}
      </Card>

      <Dialog open={Boolean(selected) || detailLoading} onOpenChange={(open) => { if (!open) setSelected(null); }}><DialogContent className="flex max-h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] max-w-2xl flex-col gap-0 overflow-hidden p-0"><DialogHeader className="border-b px-4 py-3 pr-12"><DialogTitle>{t("conversation")}</DialogTitle></DialogHeader>{detailLoading && !selected ? <div className="grid min-h-52 place-items-center"><Loader2 className="animate-spin" /></div> : selected && <div className="flex min-h-0 flex-1 flex-col p-3 sm:p-5"><div className="mb-3 flex flex-wrap items-center gap-2"><Badge variant="outline">{channelLabel(selected.channel)}</Badge><Badge variant="outline">{selected.status === "OPEN" ? t("status.open") : t("status.closed")}</Badge><span className="break-all text-xs text-slate-500">{selected.customer_email || selected.external_user_id}</span></div><div className="min-h-0 flex-1 space-y-2 overflow-y-auto rounded-lg bg-slate-50 p-3">{selected.messages.map((message) => <div key={message.sequence_number} className={cn("flex", message.role === "user" ? "justify-end" : "justify-start")}><div className={cn("max-w-[90%] whitespace-pre-wrap break-words rounded-xl px-3 py-2 text-sm leading-6", message.role === "user" ? "rounded-br-sm bg-indigo-600 text-white" : "rounded-bl-sm border bg-white text-slate-700")}>{message.content}</div></div>)}</div>{selected.status === "OPEN" && <Button className="mt-3 w-full sm:w-fit" onClick={() => void closeConversation()}>{t("closeConversation")}</Button>}</div>}</DialogContent></Dialog>
    </div>
  );
}

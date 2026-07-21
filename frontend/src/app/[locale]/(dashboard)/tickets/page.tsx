"use client";


import { useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { ChevronRight, Loader2, TicketCheck } from "lucide-react";
import toast from "react-hot-toast";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ClearFiltersButton, DateRangeFields, FilterPanel, FilterSelect, PaginationControls, ResultsSummary, UrlSearchField } from "@/components/list/ListControls";
import { useApiClient } from "@/hooks/useApiClient";
import { oneOf, safeDate, safePage, safeSize, safeUuid, useUrlListState } from "@/hooks/useUrlListState";
import { addTicketNote, getTicket, listTicketAssignees, listTickets, updateTicket, type Assignee, type Ticket, type TicketPriority, type TicketStatus } from "@/lib/tickets-api";

const STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];
const PRIORITIES: TicketPriority[] = ["LOW", "NORMAL", "HIGH", "URGENT"];
const SORTS = ["created-desc", "created-asc", "updated-desc", "priority-desc", "customer-asc"] as const;

export default function TicketsPage() {
  const t = useTranslations("Tickets");
  const format = useFormatter();
  const { request } = useApiClient();
  const { searchParams, update, clear } = useUrlListState();
  const status = oneOf(searchParams.get("status"), ["all", ...STATUSES] as const, "all");
  const priority = oneOf(searchParams.get("priority"), ["all", ...PRIORITIES] as const, "all");
  const source = oneOf(searchParams.get("source"), ["all", "WIDGET", "CUSTOM_API"] as const, "all");
  const sortValue = oneOf(searchParams.get("sort"), SORTS, "created-desc");
  const rawAssignee = searchParams.get("assignee");
  const assignee = rawAssignee === "unassigned" ? "unassigned" : safeUuid(rawAssignee) || "all";
  const createdFrom = safeDate(searchParams.get("from"));
  const rawCreatedTo = safeDate(searchParams.get("to"));
  const createdTo = rawCreatedTo && (!createdFrom || rawCreatedTo >= createdFrom) ? rawCreatedTo : "";
  const page = safePage(searchParams.get("page"));
  const size = safeSize(searchParams.get("size"));
  const urlQuery = (searchParams.get("q") ?? "").slice(0, 200);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [total, setTotal] = useState(0);
  const [assignees, setAssignees] = useState<Assignee[]>([]);
  const [selected, setSelected] = useState<Ticket | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [loadedKey, setLoadedKey] = useState("");
  const [openingId, setOpeningId] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [revision, setRevision] = useState(0);
  const formatDate = (value: string) => format.dateTime(new Date(value), { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  const sourceLabel = (value: Ticket["source"]) => value === "WIDGET" ? t("widget") : t("api");
  const statusLabel = (value: TicketStatus) => ({ OPEN: t("statuses.open"), IN_PROGRESS: t("statuses.inProgress"), RESOLVED: t("statuses.resolved"), CLOSED: t("statuses.closed") })[value];
  const priorityLabel = (value: TicketPriority) => ({ LOW: t("priorities.low"), NORMAL: t("priorities.normal"), HIGH: t("priorities.high"), URGENT: t("priorities.urgent") })[value];

  useEffect(() => { listTicketAssignees(request).then(setAssignees).catch((error) => toast.error(error instanceof Error ? error.message : t("fallback.assignees"))); }, [request, t]);

  const [sort, direction] = sortValue.split("-") as [string, "asc" | "desc"];
  const requestKey = [urlQuery, status, priority, source, assignee, createdFrom, createdTo, sortValue, page, size, revision].join("|");
  const refreshing = loaded && loadedKey !== requestKey;
  const hasFilters = Boolean(urlQuery || status !== "all" || priority !== "all" || source !== "all" || assignee !== "all" || createdFrom || createdTo || sortValue !== "created-desc");
  useEffect(() => {
    const controller = new AbortController();
    listTickets(request, {
      status: status === "all" ? undefined : status,
      priority: priority === "all" ? undefined : priority,
      source: source === "all" ? undefined : source,
      assignedTo: assignee !== "all" && assignee !== "unassigned" ? assignee : undefined,
      unassigned: assignee === "unassigned",
      q: urlQuery || undefined,
      createdFrom: createdFrom || undefined,
      createdTo: createdTo || undefined,
      sort, direction, page: page - 1, size, signal: controller.signal,
    }).then((result) => {
      if (controller.signal.aborted) return;
      const pages = Math.max(1, Math.ceil(result.total / size));
      if (page > pages) { update({ page: pages === 1 ? null : pages }, false); return; }
      setTickets(result.items); setTotal(result.total); setLoaded(true); setLoadedKey(requestKey);
    }).catch((error) => { if (!(error instanceof DOMException && error.name === "AbortError")) toast.error(error instanceof Error ? error.message : t("fallback.load")); })
      ;
    return () => controller.abort();
  }, [assignee, createdFrom, createdTo, direction, page, priority, request, requestKey, size, sort, source, status, t, update, urlQuery]);

  async function openTicket(id: string) { setOpeningId(id); try { setSelected(await getTicket(request, id)); } catch (error) { toast.error(error instanceof Error ? error.message : t("fallback.detail")); } finally { setOpeningId(null); } }
  async function patchTicket(payload: { status?: TicketStatus; priority?: TicketPriority; assignedTo?: string; clearAssignee?: boolean }) {
    if (!selected) return;
    try { const updated = await updateTicket(request, selected.id, payload); setSelected(updated); setRevision((value) => value + 1); }
    catch (error) { toast.error(error instanceof Error ? error.message : t("fallback.update")); }
  }
  async function submitNote() { if (!selected || !note.trim()) return; try { const created = await addTicketNote(request, selected.id, note.trim()); setSelected({ ...selected, notes: [...selected.notes, created] }); setNote(""); } catch (error) { toast.error(error instanceof Error ? error.message : t("fallback.note")); } }

  return <div className="space-y-4 sm:space-y-6">
    <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between"><h2 className="text-xl font-semibold text-slate-800">{t("title")}</h2><p className="text-sm text-slate-500">{t("description")}</p></div>
    <FilterPanel>
      <UrlSearchField key={urlQuery} initialValue={urlQuery} onDebouncedChange={(value) => update({ q: value || null })} placeholder={t("searchPlaceholder")} />
      <FilterSelect label={t("status")} value={status} onChange={(value) => update({ status: value === "all" ? null : value })}><option value="all">{t("allStatuses")}</option>{STATUSES.map((value) => <option key={value} value={value}>{statusLabel(value)}</option>)}</FilterSelect>
      <FilterSelect label={t("priority")} value={priority} onChange={(value) => update({ priority: value === "all" ? null : value })}><option value="all">{t("allPriorities")}</option>{PRIORITIES.map((value) => <option key={value}>{priorityLabel(value)}</option>)}</FilterSelect>
      <FilterSelect label={t("source")} value={source} onChange={(value) => update({ source: value === "all" ? null : value })}><option value="all">{t("allSources")}</option><option value="WIDGET">{t("widget")}</option><option value="CUSTOM_API">Custom API</option></FilterSelect>
      <FilterSelect label={t("assignee")} value={assignee} onChange={(value) => update({ assignee: value === "all" ? null : value })}><option value="all">{t("allAssignees")}</option><option value="unassigned">{t("unassigned")}</option>{assignees.map((item) => <option key={item.id} value={item.id}>{item.fullName}</option>)}</FilterSelect>
      <DateRangeFields prefix={t("created")} from={createdFrom} to={createdTo} onFromChange={(value) => update({ from: value || null })} onToChange={(value) => update({ to: value || null })} />
      <FilterSelect label={t("sort")} value={sortValue} onChange={(value) => update({ sort: value === "created-desc" ? null : value })}><option value="created-desc">{t("sortOptions.newest")}</option><option value="created-asc">{t("sortOptions.oldest")}</option><option value="updated-desc">{t("sortOptions.updated")}</option><option value="priority-desc">{t("sortOptions.priority")}</option><option value="customer-asc">{t("sortOptions.customer")}</option></FilterSelect>
      <ClearFiltersButton disabled={!hasFilters} onClick={clear} />
    </FilterPanel>
    <ResultsSummary total={total} refreshing={refreshing} />
    <Card className="overflow-hidden">{!loaded ? <div className="grid min-h-56 place-items-center"><Loader2 className="animate-spin text-indigo-600" /></div> : tickets.length === 0 ? <div className="px-4 py-16 text-center"><TicketCheck className="mx-auto mb-3 size-9 text-slate-300" /><p className="text-sm font-medium text-slate-500">{hasFilters ? t("noMatches") : t("empty")}</p></div> : <>
      <div className="divide-y divide-slate-200 lg:hidden">{tickets.map((ticket) => <button key={ticket.id} type="button" disabled={openingId !== null} onClick={() => void openTicket(ticket.id)} className="flex w-full items-start gap-3 p-4 text-left hover:bg-slate-50 disabled:opacity-60"><span className="grid size-10 shrink-0 place-items-center rounded-lg bg-indigo-50 text-indigo-600">{openingId === ticket.id ? <Loader2 className="size-4 animate-spin" /> : <TicketCheck className="size-4" />}</span><span className="min-w-0 flex-1"><span className="block break-words text-sm font-semibold">{ticket.title}</span><span className="block truncate text-xs text-slate-500">{ticket.customerName || ticket.customerEmail}</span><span className="mt-2 flex flex-wrap gap-1.5"><Badge variant="outline" className="text-[10px]">{priorityLabel(ticket.priority)}</Badge><Badge variant="outline" className="text-[10px]">{statusLabel(ticket.status)}</Badge><span className="text-xs text-slate-400">{sourceLabel(ticket.source)} · {formatDate(ticket.createdAt)}</span></span></span><ChevronRight className="mt-2 size-4 text-slate-400" /></button>)}</div>
      <div className="hidden lg:block"><Table><TableHeader><TableRow><TableHead>{t("table.ticket")}</TableHead><TableHead>{t("table.customer")}</TableHead><TableHead>{t("table.source")}</TableHead><TableHead>{t("table.priority")}</TableHead><TableHead>{t("table.status")}</TableHead><TableHead>{t("table.created")}</TableHead></TableRow></TableHeader><TableBody>{tickets.map((ticket) => <TableRow key={ticket.id} className="cursor-pointer" tabIndex={0} onClick={() => void openTicket(ticket.id)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); void openTicket(ticket.id); } }}><TableCell><p className="max-w-72 truncate font-medium">{ticket.title}</p><p className="font-mono text-xs text-slate-400">{ticket.id.slice(0, 8)}</p></TableCell><TableCell><p className="max-w-56 truncate">{ticket.customerEmail}</p></TableCell><TableCell><Badge variant="outline">{sourceLabel(ticket.source)}</Badge></TableCell><TableCell><Badge variant="outline">{priorityLabel(ticket.priority)}</Badge></TableCell><TableCell><Badge variant="outline">{statusLabel(ticket.status)}</Badge></TableCell><TableCell>{formatDate(ticket.createdAt)}</TableCell></TableRow>)}</TableBody></Table></div>
    </>}{loaded && <PaginationControls page={page} size={size} total={total} onPageChange={(value) => update({ page: value === 1 ? null : value }, false)} onSizeChange={(value) => update({ size: value === 20 ? null : value })} />}</Card>

    <Dialog open={Boolean(selected)} onOpenChange={(open) => { if (!open) { setSelected(null); setNote(""); } }}><DialogContent className="flex max-h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] max-w-3xl flex-col gap-0 overflow-hidden p-0"><DialogHeader className="border-b px-4 py-3 pr-12"><DialogTitle className="break-words">{selected?.title}</DialogTitle></DialogHeader>{selected && <div className="min-h-0 flex-1 overflow-y-auto p-4"><div className="grid gap-5 md:grid-cols-[minmax(0,1fr)_220px]"><div className="min-w-0 space-y-4"><div><p className="mb-1 text-xs font-medium uppercase text-slate-500">{t("customer")}</p><p className="break-words text-sm">{selected.customerName || t("customer")} <span className="break-all text-slate-500">&lt;{selected.customerEmail}&gt;</span></p></div><div><p className="mb-1 text-xs font-medium uppercase text-slate-500">{t("ticketDescription")}</p><p className="whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-3 text-sm leading-6">{selected.description}</p></div><div className="space-y-2"><p className="text-xs font-medium uppercase text-slate-500">{t("internalNotes")}</p>{selected.notes.length === 0 && <p className="rounded-lg border border-dashed p-3 text-sm text-slate-400">{t("noNotes")}</p>}{selected.notes.map((item) => <div key={item.id} className="rounded-lg border p-3 text-sm"><p className="whitespace-pre-wrap break-words">{item.content}</p><p className="mt-1 text-xs text-slate-400">{item.authorName} · {formatDate(item.createdAt)}</p></div>)}<div className="flex flex-col gap-2 sm:flex-row"><Input value={note} onChange={(event) => setNote(event.target.value)} placeholder={t("addNotePlaceholder")} /><Button disabled={!note.trim()} onClick={() => void submitNote()}>{t("addNote")}</Button></div></div></div><aside className="space-y-4 rounded-xl bg-slate-50 p-3 md:self-start"><Label className="space-y-1.5">{t("status")}<select className="h-10 w-full rounded-md border bg-white px-2 text-sm" value={selected.status} onChange={(event) => void patchTicket({ status: event.target.value as TicketStatus })}>{STATUSES.map((value) => <option key={value} value={value}>{statusLabel(value)}</option>)}</select></Label><Label className="space-y-1.5">{t("priority")}<select className="h-10 w-full rounded-md border bg-white px-2 text-sm" value={selected.priority} onChange={(event) => void patchTicket({ priority: event.target.value as TicketPriority })}>{PRIORITIES.map((value) => <option key={value} value={value}>{priorityLabel(value)}</option>)}</select></Label><Label className="space-y-1.5">{t("assignee")}<select className="h-10 w-full rounded-md border bg-white px-2 text-sm" value={selected.assignedTo ?? ""} onChange={(event) => void patchTicket(event.target.value ? { assignedTo: event.target.value } : { clearAssignee: true })}><option value="">{t("unassigned")}</option>{assignees.map((item) => <option key={item.id} value={item.id}>{item.fullName}</option>)}</select></Label><div className="text-xs leading-5 text-slate-500"><p>{t("channelValue", { value: sourceLabel(selected.source) })}</p><p>{t("createdValue", { value: formatDate(selected.createdAt) })}</p><p>{t("conversationValue", { value: selected.sessionId.slice(0, 8) })}</p></div></aside></div></div>}</DialogContent></Dialog>
  </div>;
}

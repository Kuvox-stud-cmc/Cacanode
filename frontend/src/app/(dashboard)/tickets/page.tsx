"use client";

import { useEffect, useState } from "react";
import { ChevronRight, Loader2, TicketCheck } from "lucide-react";
import toast from "react-hot-toast";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useApiClient } from "@/hooks/useApiClient";
import {
  addTicketNote,
  getTicket,
  listTicketAssignees,
  listTickets,
  updateTicket,
  type Assignee,
  type Ticket,
  type TicketPriority,
  type TicketStatus,
} from "@/lib/tickets-api";

const STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];
const PRIORITIES: TicketPriority[] = ["LOW", "NORMAL", "HIGH", "URGENT"];

function formatDate(value: string): string {
  return new Date(value).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function sourceLabel(source: Ticket["source"]): string {
  return source === "WIDGET" ? "Widget" : "API";
}

function statusLabel(status: TicketStatus): string {
  return status.replaceAll("_", " ");
}

export default function TicketsPage() {
  const { request } = useApiClient();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [assignees, setAssignees] = useState<Assignee[]>([]);
  const [selected, setSelected] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [openingId, setOpeningId] = useState<string | null>(null);
  const [note, setNote] = useState("");

  useEffect(() => {
    let cancelled = false;
    Promise.all([listTickets(request), listTicketAssignees(request)])
      .then(([ticketResult, assigneeResult]) => {
        if (cancelled) return;
        setTickets(ticketResult);
        setAssignees(assigneeResult);
      })
      .catch((error) => toast.error(
        error instanceof Error ? error.message : "Unable to load tickets",
      ))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [request]);

  async function openTicket(id: string) {
    setOpeningId(id);
    try {
      setSelected(await getTicket(request, id));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to load ticket");
    } finally {
      setOpeningId(null);
    }
  }

  async function patchTicket(payload: {
    status?: TicketStatus;
    priority?: TicketPriority;
    assignedTo?: string;
    clearAssignee?: boolean;
  }) {
    if (!selected) return;
    try {
      const updated = await updateTicket(request, selected.id, payload);
      setSelected(updated);
      setTickets((current) => current.map((ticket) => (
        ticket.id === updated.id ? updated : ticket
      )));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update ticket");
    }
  }

  async function submitNote() {
    if (!selected || !note.trim()) return;
    try {
      const created = await addTicketNote(request, selected.id, note.trim());
      setSelected({ ...selected, notes: [...selected.notes, created] });
      setNote("");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to add note");
    }
  }

  return (
    <div className="space-y-4 sm:space-y-6">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <h2 className="text-xl font-semibold text-slate-800">Tickets</h2>
        <p className="text-sm leading-5 text-slate-500">
          Created by customer widget and API conversations
        </p>
      </div>

      <Card className="overflow-hidden">
        {loading ? (
          <div className="grid min-h-56 place-items-center">
            <Loader2 className="animate-spin text-indigo-600" />
          </div>
        ) : tickets.length === 0 ? (
          <div className="px-4 py-16 text-center">
            <TicketCheck className="mx-auto mb-3 size-9 text-slate-300" />
            <p className="text-sm text-slate-500">No tickets</p>
          </div>
        ) : (
          <>
            <div className="divide-y divide-slate-200 lg:hidden">
              {tickets.map((ticket) => (
                <button
                  key={ticket.id}
                  type="button"
                  disabled={openingId !== null}
                  onClick={() => void openTicket(ticket.id)}
                  className="flex w-full items-start gap-3 p-4 text-left transition-colors hover:bg-slate-50 active:bg-slate-100 disabled:opacity-60"
                >
                  <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-indigo-50 text-indigo-600">
                    {openingId === ticket.id
                      ? <Loader2 className="size-4 animate-spin" />
                      : <TicketCheck className="size-4" />}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block break-words text-sm font-semibold leading-5 text-slate-900">
                      {ticket.title}
                    </span>
                    <span className="mt-0.5 block truncate text-xs text-slate-500">
                      {ticket.customerName || ticket.customerEmail}
                    </span>
                    <span className="mt-2 flex flex-wrap items-center gap-1.5">
                      <Badge variant="outline" className="text-[10px]">{ticket.priority}</Badge>
                      <Badge variant="outline" className="text-[10px]">{statusLabel(ticket.status)}</Badge>
                      <span className="text-xs text-slate-400">{sourceLabel(ticket.source)} · {formatDate(ticket.createdAt)}</span>
                    </span>
                  </span>
                  <ChevronRight className="mt-2 size-4 shrink-0 text-slate-400" />
                </button>
              ))}
            </div>

            <div className="hidden lg:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Ticket</TableHead>
                    <TableHead>Customer</TableHead>
                    <TableHead>Source</TableHead>
                    <TableHead>Priority</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Created</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {tickets.map((ticket) => (
                    <TableRow
                      key={ticket.id}
                      className="cursor-pointer"
                      tabIndex={0}
                      onClick={() => void openTicket(ticket.id)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") void openTicket(ticket.id);
                      }}
                    >
                      <TableCell>
                        <p className="max-w-72 truncate font-medium">{ticket.title}</p>
                        <p className="font-mono text-xs text-slate-400">{ticket.id.slice(0, 8)}</p>
                      </TableCell>
                      <TableCell><p className="max-w-56 truncate">{ticket.customerEmail}</p></TableCell>
                      <TableCell><Badge variant="outline">{sourceLabel(ticket.source)}</Badge></TableCell>
                      <TableCell><Badge variant="outline">{ticket.priority}</Badge></TableCell>
                      <TableCell><Badge variant="outline">{statusLabel(ticket.status)}</Badge></TableCell>
                      <TableCell>{formatDate(ticket.createdAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </>
        )}
      </Card>

      <Dialog
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) {
            setSelected(null);
            setNote("");
          }
        }}
      >
        <DialogContent className="flex max-h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] max-w-3xl flex-col gap-0 overflow-hidden p-0 sm:max-w-3xl">
          <DialogHeader className="shrink-0 border-b border-slate-200 px-4 py-3 pr-12 sm:px-5 sm:py-4">
            <DialogTitle className="break-words leading-5">{selected?.title}</DialogTitle>
          </DialogHeader>
          {selected && (
            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-4 sm:p-5">
              <div className="grid min-w-0 gap-5 md:grid-cols-[minmax(0,1fr)_220px]">
                <div className="min-w-0 space-y-4">
                  <div>
                    <p className="mb-1 text-xs font-medium uppercase text-slate-500">Customer</p>
                    <p className="break-words text-sm">
                      {selected.customerName || "Customer"}
                      <span className="block break-all text-slate-500 sm:ml-1 sm:inline">
                        &lt;{selected.customerEmail}&gt;
                      </span>
                    </p>
                  </div>
                  <div>
                    <p className="mb-1 text-xs font-medium uppercase text-slate-500">Description</p>
                    <p className="whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-3 text-sm leading-6 text-slate-700">
                      {selected.description}
                    </p>
                  </div>
                  <div className="space-y-2">
                    <p className="text-xs font-medium uppercase text-slate-500">Internal notes</p>
                    {selected.notes.length === 0 && (
                      <p className="rounded-lg border border-dashed p-3 text-sm text-slate-400">No internal notes yet.</p>
                    )}
                    {selected.notes.map((item) => (
                      <div key={item.id} className="rounded-lg border p-3 text-sm">
                        <p className="whitespace-pre-wrap break-words leading-6">{item.content}</p>
                        <p className="mt-1 text-xs leading-5 text-slate-400">
                          {item.authorName} · {formatDate(item.createdAt)}
                        </p>
                      </div>
                    ))}
                    <div className="flex flex-col gap-2 sm:flex-row">
                      <Input
                        value={note}
                        onChange={(event) => setNote(event.target.value)}
                        placeholder="Add internal note"
                      />
                      <Button className="w-full sm:w-auto" disabled={!note.trim()} onClick={() => void submitNote()}>
                        Add note
                      </Button>
                    </div>
                  </div>
                </div>

                <aside className="space-y-4 rounded-xl bg-slate-50 p-3 md:self-start">
                  <div className="space-y-1.5">
                    <Label>Status</Label>
                    <select
                      className="h-10 w-full rounded-md border bg-white px-2 text-sm"
                      value={selected.status}
                      onChange={(event) => void patchTicket({ status: event.target.value as TicketStatus })}
                    >
                      {STATUSES.map((status) => <option key={status}>{status}</option>)}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <Label>Priority</Label>
                    <select
                      className="h-10 w-full rounded-md border bg-white px-2 text-sm"
                      value={selected.priority}
                      onChange={(event) => void patchTicket({ priority: event.target.value as TicketPriority })}
                    >
                      {PRIORITIES.map((priority) => <option key={priority}>{priority}</option>)}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <Label>Assignee</Label>
                    <select
                      className="h-10 w-full rounded-md border bg-white px-2 text-sm"
                      value={selected.assignedTo ?? ""}
                      onChange={(event) => void patchTicket(
                        event.target.value
                          ? { assignedTo: event.target.value }
                          : { clearAssignee: true },
                      )}
                    >
                      <option value="">Unassigned</option>
                      {assignees.map((assignee) => (
                        <option key={assignee.id} value={assignee.id}>{assignee.fullName}</option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1 text-xs leading-5 text-slate-500">
                    <p>Channel: {sourceLabel(selected.source)}</p>
                    <p>Created: {formatDate(selected.createdAt)}</p>
                    <p>Conversation: {selected.sessionId.slice(0, 8)}</p>
                  </div>
                </aside>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

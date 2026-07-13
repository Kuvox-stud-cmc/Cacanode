"use client";

import { useEffect, useState } from "react";
import { Loader2, TicketCheck } from "lucide-react";
import toast from "react-hot-toast";
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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];
const PRIORITIES: TicketPriority[] = ["LOW", "NORMAL", "HIGH", "URGENT"];

export default function TicketsPage() {
  const { request } = useApiClient();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [assignees, setAssignees] = useState<Assignee[]>([]);
  const [selected, setSelected] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [note, setNote] = useState("");

  useEffect(() => {
    let cancelled = false;
    Promise.all([listTickets(request), listTicketAssignees(request)])
      .then(([ticketResult, assigneeResult]) => {
        if (cancelled) return;
        setTickets(ticketResult);
        setAssignees(assigneeResult);
      })
      .catch((error) => toast.error(error instanceof Error ? error.message : "Unable to load tickets"))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [request]);

  async function openTicket(id: string) {
    try {
      setSelected(await getTicket(request, id));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to load ticket");
    }
  }

  async function patchTicket(payload: { status?: TicketStatus; priority?: TicketPriority; assignedTo?: string; clearAssignee?: boolean }) {
    if (!selected) return;
    try {
      const updated = await updateTicket(request, selected.id, payload);
      setSelected(updated);
      setTickets((current) => current.map((ticket) => ticket.id === updated.id ? updated : ticket));
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
    <div className="space-y-6"><div className="flex items-center justify-between"><h2 className="text-xl font-semibold text-slate-800">Tickets</h2><p className="text-sm text-slate-500">Created by customer widget and API conversations</p></div><Card className="overflow-hidden">{loading ? <div className="grid min-h-56 place-items-center"><Loader2 className="animate-spin" /></div> : tickets.length === 0 ? <div className="py-16 text-center"><TicketCheck className="mx-auto mb-3 size-9 text-slate-300" /><p className="text-sm text-slate-500">No tickets</p></div> : <Table><TableHeader><TableRow><TableHead>Ticket</TableHead><TableHead>Customer</TableHead><TableHead>Source</TableHead><TableHead>Priority</TableHead><TableHead>Status</TableHead><TableHead>Created</TableHead></TableRow></TableHeader><TableBody>{tickets.map((ticket) => <TableRow key={ticket.id} className="cursor-pointer" onClick={() => void openTicket(ticket.id)}><TableCell><p className="font-medium">{ticket.title}</p><p className="font-mono text-xs text-slate-400">{ticket.id.slice(0, 8)}</p></TableCell><TableCell>{ticket.customerEmail}</TableCell><TableCell><Badge variant="outline">{ticket.source}</Badge></TableCell><TableCell><Badge variant="outline">{ticket.priority}</Badge></TableCell><TableCell><Badge variant="outline">{ticket.status}</Badge></TableCell><TableCell>{new Date(ticket.createdAt).toLocaleString()}</TableCell></TableRow>)}</TableBody></Table>}</Card>

      <Dialog open={Boolean(selected)} onOpenChange={(open) => !open && setSelected(null)}><DialogContent className="max-w-3xl"><DialogHeader><DialogTitle>{selected?.title}</DialogTitle></DialogHeader>{selected && <div className="grid gap-5 lg:grid-cols-[1fr_220px]"><div className="space-y-4"><div><p className="mb-1 text-xs font-medium uppercase text-slate-500">Customer</p><p className="text-sm">{selected.customerName || "Customer"} &lt;{selected.customerEmail}&gt;</p></div><div><p className="mb-1 text-xs font-medium uppercase text-slate-500">Description</p><p className="whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm text-slate-700">{selected.description}</p></div><div className="space-y-2"><p className="text-xs font-medium uppercase text-slate-500">Internal notes</p>{selected.notes.map((item) => <div key={item.id} className="rounded-md border p-3 text-sm"><p>{item.content}</p><p className="mt-1 text-xs text-slate-400">{item.authorName} · {new Date(item.createdAt).toLocaleString()}</p></div>)}<div className="flex gap-2"><Input value={note} onChange={(event) => setNote(event.target.value)} placeholder="Add internal note" /><Button onClick={() => void submitNote()}>Add</Button></div></div></div><aside className="space-y-4"><div className="space-y-1.5"><Label>Status</Label><select className="h-9 w-full rounded-md border px-2 text-sm" value={selected.status} onChange={(event) => void patchTicket({ status: event.target.value as TicketStatus })}>{STATUSES.map((status) => <option key={status}>{status}</option>)}</select></div><div className="space-y-1.5"><Label>Priority</Label><select className="h-9 w-full rounded-md border px-2 text-sm" value={selected.priority} onChange={(event) => void patchTicket({ priority: event.target.value as TicketPriority })}>{PRIORITIES.map((priority) => <option key={priority}>{priority}</option>)}</select></div><div className="space-y-1.5"><Label>Assignee</Label><select className="h-9 w-full rounded-md border px-2 text-sm" value={selected.assignedTo ?? ""} onChange={(event) => void patchTicket(event.target.value ? { assignedTo: event.target.value } : { clearAssignee: true })}><option value="">Unassigned</option>{assignees.map((assignee) => <option key={assignee.id} value={assignee.id}>{assignee.fullName}</option>)}</select></div><div className="text-xs text-slate-500"><p>Channel: {selected.source}</p><p>Created: {new Date(selected.createdAt).toLocaleString()}</p><p>Conversation: {selected.sessionId.slice(0, 8)}</p></div></aside></div>}</DialogContent></Dialog>
    </div>
  );
}

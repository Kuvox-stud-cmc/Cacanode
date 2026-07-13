"use client";

import { useEffect, useState } from "react";
import { Eye, Loader2, MessageSquare } from "lucide-react";
import toast from "react-hot-toast";
import { useApiClient } from "@/hooks/useApiClient";
import {
  closeCustomerConversation,
  getCustomerConversation,
  listCustomerConversations,
  type CustomerConversation,
  type CustomerConversationDetail,
} from "@/lib/conversations-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

type Filter = "all" | "OPEN" | "CLOSED";

export default function ConversationsPage() {
  const { request } = useApiClient();
  const [filter, setFilter] = useState<Filter>("all");
  const [items, setItems] = useState<CustomerConversation[]>([]);
  const [selected, setSelected] = useState<CustomerConversationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listCustomerConversations(request, filter === "all" ? undefined : filter)
      .then((result) => !cancelled && setItems(result))
      .catch((error) => toast.error(error instanceof Error ? error.message : "Unable to load conversations"))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [filter, request]);

  async function openConversation(id: string) {
    setDetailLoading(true);
    try {
      setSelected(await getCustomerConversation(request, id));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to load conversation");
    } finally {
      setDetailLoading(false);
    }
  }

  async function closeConversation() {
    if (!selected) return;
    try {
      await closeCustomerConversation(request, selected.id);
      setSelected({ ...selected, status: "CLOSED", closed_at: new Date().toISOString() });
      setItems((current) => current.map((item) => (
        item.id === selected.id ? { ...item, status: "CLOSED" } : item
      )));
      toast.success("Conversation closed");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to close conversation");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between"><h2 className="text-xl font-semibold text-slate-800">Customer Conversations</h2><p className="text-sm text-slate-500">Widget and custom API channels only</p></div>
      <div className="flex w-fit gap-1 rounded-md bg-slate-200 p-1">
        {(["all", "OPEN", "CLOSED"] as Filter[]).map((value) => <button key={value} type="button" onClick={() => setFilter(value)} className={`rounded px-4 py-1.5 text-sm ${filter === value ? "bg-white text-slate-900 shadow-sm" : "text-slate-600"}`}>{value === "all" ? "All" : value === "OPEN" ? "Open" : "Closed"}</button>)}
      </div>
      <Card className="overflow-hidden">
        {loading ? <div className="grid min-h-56 place-items-center"><Loader2 className="animate-spin text-indigo-600" /></div> : items.length === 0 ? <div className="py-16 text-center"><MessageSquare className="mx-auto mb-3 size-9 text-slate-300" /><p className="text-sm text-slate-500">No customer conversations</p></div> : <Table><TableHeader><TableRow><TableHead>Customer</TableHead><TableHead>Channel</TableHead><TableHead>Messages</TableHead><TableHead>Started</TableHead><TableHead>Status</TableHead><TableHead /></TableRow></TableHeader><TableBody>{items.map((item) => <TableRow key={item.id} className="cursor-pointer" onClick={() => void openConversation(item.id)}><TableCell><p className="font-medium">{item.customer_name || item.customer_email || item.external_user_id || "Anonymous"}</p><p className="text-xs text-slate-500">{item.customer_email}</p></TableCell><TableCell><Badge variant="outline">{item.channel === "WIDGET" ? "Widget" : "API"}</Badge></TableCell><TableCell>{item.message_count}</TableCell><TableCell>{new Date(item.created_at).toLocaleString()}</TableCell><TableCell><Badge variant="outline">{item.status}</Badge></TableCell><TableCell><Eye className="size-4 text-slate-400" /></TableCell></TableRow>)}</TableBody></Table>}
      </Card>

      <Dialog open={Boolean(selected) || detailLoading} onOpenChange={(open) => !open && setSelected(null)}><DialogContent className="max-w-2xl"><DialogHeader><DialogTitle>Conversation</DialogTitle></DialogHeader>{detailLoading && !selected ? <div className="grid min-h-52 place-items-center"><Loader2 className="animate-spin" /></div> : selected && <div className="space-y-4"><div className="flex flex-wrap items-center gap-2 text-sm"><Badge variant="outline">{selected.channel}</Badge><Badge variant="outline">{selected.status}</Badge><span className="text-slate-500">{selected.customer_email || selected.external_user_id}</span></div><div className="max-h-[55vh] space-y-2 overflow-y-auto rounded-md bg-slate-50 p-4">{selected.messages.map((message) => <div key={message.sequence_number} className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}><div className={`max-w-[82%] rounded-md px-3 py-2 text-sm ${message.role === "user" ? "bg-indigo-600 text-white" : "border bg-white text-slate-700"}`}>{message.content}</div></div>)}</div>{selected.status === "OPEN" && <Button onClick={() => void closeConversation()}>Close conversation</Button>}</div>}</DialogContent></Dialog>
    </div>
  );
}

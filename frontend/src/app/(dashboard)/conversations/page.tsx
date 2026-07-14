"use client";

import { useEffect, useState } from "react";
import { ChevronRight, Eye, Loader2, MessageSquare } from "lucide-react";
import toast from "react-hot-toast";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useApiClient } from "@/hooks/useApiClient";
import {
  closeCustomerConversation,
  getCustomerConversation,
  listCustomerConversations,
  type CustomerConversation,
  type CustomerConversationDetail,
} from "@/lib/conversations-api";
import { cn } from "@/lib/utils";

type Filter = "all" | "OPEN" | "CLOSED";

function customerLabel(item: CustomerConversation): string {
  return item.customer_name || item.customer_email || item.external_user_id || "Anonymous";
}

function channelLabel(channel: CustomerConversation["channel"]): string {
  return channel === "WIDGET" ? "Widget" : "API";
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

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
      .catch((error) => toast.error(
        error instanceof Error ? error.message : "Unable to load conversations",
      ))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
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
    <div className="space-y-4 sm:space-y-6">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <h2 className="text-xl font-semibold text-slate-800">Customer Conversations</h2>
        <p className="text-sm leading-5 text-slate-500">Widget and custom API channels only</p>
      </div>

      <div className="grid w-full grid-cols-3 gap-1 rounded-lg bg-slate-200 p-1 sm:w-fit">
        {(["all", "OPEN", "CLOSED"] as Filter[]).map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => setFilter(value)}
            className={cn(
              "min-h-9 rounded-md px-3 py-1.5 text-sm font-medium transition-colors sm:px-4",
              filter === value
                ? "bg-white text-slate-900 shadow-sm"
                : "text-slate-600 hover:text-slate-900",
            )}
          >
            {value === "all" ? "All" : value === "OPEN" ? "Open" : "Closed"}
          </button>
        ))}
      </div>

      <Card className="overflow-hidden">
        {loading ? (
          <div className="grid min-h-56 place-items-center">
            <Loader2 className="animate-spin text-indigo-600" />
          </div>
        ) : items.length === 0 ? (
          <div className="px-4 py-16 text-center">
            <MessageSquare className="mx-auto mb-3 size-9 text-slate-300" />
            <p className="text-sm text-slate-500">No customer conversations</p>
          </div>
        ) : (
          <>
            <div className="divide-y divide-slate-200 lg:hidden">
              {items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => void openConversation(item.id)}
                  className="flex w-full items-start gap-3 p-4 text-left transition-colors hover:bg-slate-50 active:bg-slate-100"
                >
                  <span className="grid size-10 shrink-0 place-items-center rounded-full bg-indigo-50 text-indigo-600">
                    <MessageSquare className="size-4" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex min-w-0 items-start justify-between gap-2">
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-semibold text-slate-900">
                          {customerLabel(item)}
                        </span>
                        {item.customer_email && item.customer_name && (
                          <span className="block truncate text-xs text-slate-500">{item.customer_email}</span>
                        )}
                      </span>
                      <Badge variant="outline" className="shrink-0 text-[10px]">{item.status}</Badge>
                    </span>
                    <span className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-500">
                      <span>{channelLabel(item.channel)}</span>
                      <span aria-hidden="true">·</span>
                      <span>{item.message_count} messages</span>
                      <span aria-hidden="true">·</span>
                      <span>{formatDate(item.created_at)}</span>
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
                    <TableHead>Customer</TableHead>
                    <TableHead>Channel</TableHead>
                    <TableHead>Messages</TableHead>
                    <TableHead>Started</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow
                      key={item.id}
                      className="cursor-pointer"
                      tabIndex={0}
                      onClick={() => void openConversation(item.id)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") void openConversation(item.id);
                      }}
                    >
                      <TableCell>
                        <p className="font-medium">{customerLabel(item)}</p>
                        <p className="max-w-64 truncate text-xs text-slate-500">{item.customer_email}</p>
                      </TableCell>
                      <TableCell><Badge variant="outline">{channelLabel(item.channel)}</Badge></TableCell>
                      <TableCell>{item.message_count}</TableCell>
                      <TableCell>{formatDate(item.created_at)}</TableCell>
                      <TableCell><Badge variant="outline">{item.status}</Badge></TableCell>
                      <TableCell><Eye className="size-4 text-slate-400" /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </>
        )}
      </Card>

      <Dialog
        open={Boolean(selected) || detailLoading}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      >
        <DialogContent className="flex max-h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] max-w-2xl flex-col gap-0 overflow-hidden p-0 sm:max-w-2xl">
          <DialogHeader className="shrink-0 border-b border-slate-200 px-4 py-3 pr-12 sm:px-5 sm:py-4">
            <DialogTitle>Conversation</DialogTitle>
          </DialogHeader>
          {detailLoading && !selected ? (
            <div className="grid min-h-52 place-items-center">
              <Loader2 className="animate-spin" />
            </div>
          ) : selected && (
            <div className="flex min-h-0 flex-1 flex-col p-3 sm:p-5">
              <div className="mb-3 flex shrink-0 flex-wrap items-center gap-2 text-sm">
                <Badge variant="outline">{channelLabel(selected.channel)}</Badge>
                <Badge variant="outline">{selected.status}</Badge>
                <span className="min-w-0 break-all text-xs text-slate-500 sm:text-sm">
                  {selected.customer_email || selected.external_user_id}
                </span>
              </div>
              <div className="min-h-0 flex-1 space-y-2 overflow-y-auto overscroll-contain rounded-lg bg-slate-50 p-3 sm:p-4">
                {selected.messages.map((message) => (
                  <div
                    key={message.sequence_number}
                    className={cn("flex", message.role === "user" ? "justify-end" : "justify-start")}
                  >
                    <div className={cn(
                      "max-w-[90%] whitespace-pre-wrap break-words rounded-xl px-3 py-2 text-sm leading-6 sm:max-w-[82%]",
                      message.role === "user"
                        ? "rounded-br-sm bg-indigo-600 text-white"
                        : "rounded-bl-sm border bg-white text-slate-700",
                    )}>
                      {message.content}
                    </div>
                  </div>
                ))}
              </div>
              {selected.status === "OPEN" && (
                <Button className="mt-3 w-full shrink-0 sm:w-fit" onClick={() => void closeConversation()}>
                  Close conversation
                </Button>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

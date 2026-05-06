"use client";

import { useState } from "react";
import { mockConversations } from "@/lib/mock-data";
import type { Conversation } from "@/types";
import { Card } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { MessageSquare, Eye } from "lucide-react";

type Filter = "all" | "open" | "resolved";

function StatusBadge({ status }: { status: Conversation["status"] }) {
  return (
    <span
      className={`px-2 py-0.5 rounded-full text-xs font-medium ${
        status === "open"
          ? "bg-blue-100 text-blue-800"
          : "bg-green-100 text-green-800"
      }`}
    >
      {status}
    </span>
  );
}

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

function formatDuration(secs: number): string {
  if (secs < 60) return `${secs}s`;
  return `${Math.floor(secs / 60)}m ${secs % 60}s`;
}

function TableSkeleton() {
  return (
    <div className="space-y-3 p-4">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex gap-4">
          <Skeleton className="h-5 w-28" />
          <Skeleton className="h-5 w-12" />
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-8" />
        </div>
      ))}
    </div>
  );
}

export default function ConversationsPage() {
  const [filter, setFilter] = useState<Filter>("all");
  const [loading] = useState(false);
  const [selected, setSelected] = useState<Conversation | null>(null);

  const filtered =
    filter === "all"
      ? mockConversations
      : mockConversations.filter((c) => c.status === filter);

  const filterCounts = {
    all: mockConversations.length,
    open: mockConversations.filter((c) => c.status === "open").length,
    resolved: mockConversations.filter((c) => c.status === "resolved").length,
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-800">Conversations</h2>
      </div>

      {/* Filter tabs */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        {(["all", "open", "resolved"] as Filter[]).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors capitalize ${
              filter === f
                ? "bg-white shadow-sm text-slate-800"
                : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {f}
            <span className="ml-1.5 text-xs text-slate-400">({filterCounts[f]})</span>
          </button>
        ))}
      </div>

      <Card>
        {loading ? (
          <TableSkeleton />
        ) : filtered.length === 0 ? (
          <div className="py-16 text-center">
            <MessageSquare className="w-10 h-10 text-slate-300 mx-auto mb-3" />
            <p className="text-slate-500 font-medium">No conversations</p>
            <p className="text-sm text-slate-400 mt-1">Conversations will appear here once visitors start chatting.</p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Visitor</TableHead>
                <TableHead>Messages</TableHead>
                <TableHead>Started</TableHead>
                <TableHead>Duration</TableHead>
                <TableHead>Status</TableHead>
                <TableHead></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((conv) => (
                <TableRow key={conv.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setSelected(conv)}>
                  <TableCell>
                    <span className="font-mono text-sm text-slate-600">{conv.visitorId}</span>
                  </TableCell>
                  <TableCell className="text-sm text-slate-600">{conv.messageCount}</TableCell>
                  <TableCell className="text-sm text-slate-500">{relativeTime(conv.startedAt)}</TableCell>
                  <TableCell className="text-sm text-slate-500">{formatDuration(conv.durationSeconds)}</TableCell>
                  <TableCell>
                    <StatusBadge status={conv.status} />
                  </TableCell>
                  <TableCell>
                    <button
                      onClick={(e) => { e.stopPropagation(); setSelected(conv); }}
                      className="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-slate-700 transition-colors"
                    >
                      <Eye className="w-4 h-4" />
                    </button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      {/* Conversation detail dialog */}
      <Dialog open={!!selected} onOpenChange={() => setSelected(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Conversation detail</DialogTitle>
          </DialogHeader>

          {selected && (
            <div className="space-y-4">
              <div className="flex items-center justify-between text-sm">
                <div>
                  <span className="text-slate-500">Visitor: </span>
                  <span className="font-mono text-slate-700">{selected.visitorId}</span>
                </div>
                <StatusBadge status={selected.status} />
              </div>
              <div className="flex gap-4 text-xs text-slate-500">
                <span>Started: {new Date(selected.startedAt).toLocaleString()}</span>
                <span>Duration: {formatDuration(selected.durationSeconds)}</span>
              </div>

              <div className="border-t border-slate-100 pt-3 space-y-2 max-h-64 overflow-y-auto">
                {selected.messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
                  >
                    <div
                      className={`max-w-[80%] px-3 py-2 rounded-xl text-sm ${
                        msg.role === "user"
                          ? "bg-indigo-600 text-white rounded-br-none"
                          : "bg-slate-100 text-slate-700 rounded-bl-none"
                      }`}
                    >
                      {msg.content}
                    </div>
                  </div>
                ))}
              </div>

              <div className="flex gap-2 pt-2 border-t border-slate-100">
                {selected.status === "open" && (
                  <Button
                    size="sm"
                    className="bg-green-600 hover:bg-green-700 text-white"
                    onClick={() => setSelected(null)}
                  >
                    Mark resolved
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setSelected(null)}
                >
                  Close
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

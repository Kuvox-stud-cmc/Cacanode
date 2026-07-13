"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useApiClient } from "@/hooks/useApiClient";
import { useAuthStore } from "@/components/providers/StoreProvider";
import { getDashboardSummary, type DashboardSummary } from "@/lib/usage-api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  FileText,
  MessageSquare,
  HardDrive,
  Users,
  Upload,
  UserPlus,
  TrendingUp,
  TrendingDown,
  Minus,
  AlertCircle,
  Inbox,
} from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";

function StatusBadge({ status }: { status: string }) {
  const classes: Record<string, string> = {
    PENDING: "bg-yellow-100 text-yellow-800",
    PROCESSING: "bg-blue-100 text-blue-800",
    COMPLETED: "bg-green-100 text-green-800",
    FAILED: "bg-red-100 text-red-800",
  };
  const labels: Record<string, string> = {
    PENDING: "Pending",
    PROCESSING: "Indexing",
    COMPLETED: "Completed",
    FAILED: "Failed",
  };
  return (
    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${classes[status] ?? ""}`}>
      {labels[status] ?? status}
    </span>
  );
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export default function DashboardPage() {
  const { request } = useApiClient();
  const user = useAuthStore((state) => state.user);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSummary = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSummary(await getDashboardSummary(request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to load the dashboard");
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    const task = window.setTimeout(() => void loadSummary(), 0);
    return () => window.clearTimeout(task);
  }, [loadSummary]);

  const stats = useMemo(() => {
    const data = summary ?? {
      totalDocuments: 0, documentsAddedThisWeek: 0, userMessagesThisMonth: 0,
      userMessagesPreviousMonth: 0, storedDocumentBytes: 0, storageLimitBytes: 0,
      activeUsers: 0, activeUsersAddedThisWeek: 0,
    };
    const messageChange = data.userMessagesPreviousMonth === 0
      ? 0
      : ((data.userMessagesThisMonth - data.userMessagesPreviousMonth) / data.userMessagesPreviousMonth) * 100;
    return [
      { label: "Total Documents", value: data.totalDocuments.toLocaleString(), icon: FileText, trend: data.documentsAddedThisWeek > 0 ? "up" : "neutral", trendValue: `+${data.documentsAddedThisWeek} this week` },
      { label: "Messages This Month", value: data.userMessagesThisMonth.toLocaleString(), icon: MessageSquare, trend: messageChange > 0 ? "up" : messageChange < 0 ? "down" : "neutral", trendValue: `${messageChange > 0 ? "+" : ""}${messageChange.toFixed(0)}% vs last month` },
      { label: "Storage Used", value: formatBytes(data.storedDocumentBytes), icon: HardDrive, trend: "neutral", trendValue: `of ${formatBytes(data.storageLimitBytes)}` },
      { label: "Active Users", value: data.activeUsers.toLocaleString(), icon: Users, trend: data.activeUsersAddedThisWeek > 0 ? "up" : "neutral", trendValue: `+${data.activeUsersAddedThisWeek} this week` },
    ] as const;
  }, [summary]);

  return (
    <div className="space-y-6">
      {/* Action buttons */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-800">Overview</h2>
        <div className="flex gap-2">
          <Link href="/documents" className={buttonVariants({ variant: "outline", size: "sm", className: "gap-1.5" })}><Upload className="w-4 h-4" />Upload</Link>
          {user?.role === "TENANT_ADMIN" && <Link href="/users" className={cn(buttonVariants({ size: "sm" }), "gap-1.5 bg-indigo-600 text-white hover:bg-indigo-700")}><UserPlus className="w-4 h-4" />Invite User</Link>}
        </div>
      </div>

      {error && <div role="alert" className="flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"><span className="flex items-center gap-2"><AlertCircle className="size-4" />{error}</span><Button size="sm" variant="outline" onClick={() => void loadSummary()}>Retry</Button></div>}

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {loading ? Array.from({ length: 4 }).map((_, index) => <Card key={index}><CardHeader><Skeleton className="h-4 w-28" /></CardHeader><CardContent><Skeleton className="mb-2 h-8 w-20" /><Skeleton className="h-3 w-32" /></CardContent></Card>) : stats.map((stat) => {
          const Icon = stat.icon;
          const TrendIcon =
            stat.trend === "up" ? TrendingUp : stat.trend === "down" ? TrendingDown : Minus;
          const trendColor =
            stat.trend === "up"
              ? "text-green-600"
              : stat.trend === "down"
              ? "text-red-500"
              : "text-slate-400";
          return (
            <Card key={stat.label}>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium text-slate-500">
                  {stat.label}
                </CardTitle>
                {Icon && <Icon className="w-4 h-4 text-indigo-500" />}
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-slate-800">{stat.value}</div>
                <div className={`flex items-center gap-1 text-xs mt-1 ${trendColor}`}>
                  <TrendIcon className="w-3 h-3" />
                  {stat.trendValue}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Recent uploads */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Recent Uploads</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>File Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Size</TableHead>
                <TableHead>Date</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? Array.from({ length: 5 }).map((_, index) => <TableRow key={index}><TableCell colSpan={5}><Skeleton className="h-7 w-full" /></TableCell></TableRow>) : (summary?.recentDocuments.length ?? 0) === 0 ? <TableRow><TableCell colSpan={5} className="h-40 text-center"><Inbox className="mx-auto mb-2 size-8 text-slate-300" /><p className="font-medium text-slate-500">No documents uploaded yet</p><p className="mt-1 text-sm text-slate-400">Your latest uploads will appear here.</p></TableCell></TableRow> : summary!.recentDocuments.map((doc) => (
                <TableRow key={doc.id}>
                  <TableCell className="font-medium">{doc.fileName}</TableCell>
                  <TableCell>
                    <Badge variant="secondary" className="uppercase text-xs">
                      {doc.fileType}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={doc.status} />
                  </TableCell>
                  <TableCell className="text-slate-500 text-sm">
                    {formatBytes(doc.fileSizeBytes)}
                  </TableCell>
                  <TableCell className="text-slate-500 text-sm">
                    {formatDate(doc.uploadedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

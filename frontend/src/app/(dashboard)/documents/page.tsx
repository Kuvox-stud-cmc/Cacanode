"use client";

import {
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
} from "react";
import toast from "react-hot-toast";
import type { Document, DocumentStatus } from "@/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";
import { Cloud, FileText, Loader2, Upload } from "lucide-react";
import { useApiClient } from "@/hooks/useApiClient";
import {
  fileTypeFromName,
  getDocumentStatusApi,
  isTerminalDocumentStatus,
  listDocumentsApi,
  uploadDocumentApi,
} from "@/lib/documents-api";
import { publicConfig } from "@/lib/public-config";

type DashboardDocument = Document & {
  localId?: string;
  uploadState?: "UPLOADING";
};

function StatusBadge({
  status,
  uploadState,
}: {
  status: DocumentStatus;
  uploadState?: "UPLOADING";
}) {
  if (uploadState === "UPLOADING") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
        <Loader2 className="size-3 animate-spin" /> Uploading
      </span>
    );
  }

  const classes: Record<DocumentStatus, string> = {
    PENDING: "bg-yellow-100 text-yellow-800",
    PROCESSING: "bg-blue-100 text-blue-800",
    COMPLETED: "bg-green-100 text-green-800",
    FAILED: "bg-red-100 text-red-800",
  };
  const labels: Record<DocumentStatus, string> = {
    PENDING: "Pending",
    PROCESSING: "Indexing",
    COMPLETED: "Completed",
    FAILED: "Failed",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${classes[status]}`}>
      {labels[status]}
    </span>
  );
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function makeId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function isSupportedFile(file: File): boolean {
  const lower = file.name.toLowerCase();
  return lower.endsWith(".txt") || lower.endsWith(".pdf");
}

function TableSkeleton() {
  return (
    <div className="space-y-3 p-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="flex gap-4">
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-24" />
        </div>
      ))}
    </div>
  );
}

export default function DocumentsPage() {
  const { request } = useApiClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [documents, setDocuments] = useState<DashboardDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [isDragging, setIsDragging] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listDocumentsApi(request, publicConfig.demoKnowledgeBaseId)
      .then((items) => {
        if (!cancelled) setDocuments(items);
      })
      .catch((error) => {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load documents");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [request]);

  useEffect(() => {
    const pollable = documents.filter(
      (document) => !document.uploadState && !isTerminalDocumentStatus(document.status),
    );
    if (pollable.length === 0) return;

    let cancelled = false;
    const poll = async () => {
      const updates = await Promise.allSettled(
        pollable.map((document) => getDocumentStatusApi(request, document.id)),
      );
      if (cancelled) return;
      setDocuments((current) =>
        current.map((document) => {
          const index = pollable.findIndex((item) => item.id === document.id);
          if (index < 0) return document;
          const result = updates[index];
          if (!result || result.status !== "fulfilled") return document;
          return {
            ...document,
            status: result.value.status,
            chunkCount: result.value.chunkCount,
            errorMessage: result.value.errorMessage,
          };
        }),
      );
    };

    const timer = window.setInterval(() => {
      void poll();
    }, 1800);
    void poll();

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [documents, request]);

  async function uploadFiles(files: File[]) {
    for (const file of files) {
      if (!isSupportedFile(file)) {
        toast.error(`${file.name} is not supported. Upload TXT or PDF files.`);
        continue;
      }

      const localId = makeId();
      const uploading: DashboardDocument = {
        id: localId,
        localId,
        fileName: file.name,
        fileType: fileTypeFromName(file.name),
        status: "PENDING",
        uploadState: "UPLOADING",
        fileSizeBytes: file.size,
        jobId: localId,
        knowledgeBaseId: publicConfig.demoKnowledgeBaseId,
        uploadedAt: new Date().toISOString(),
      };
      setDocuments((current) => [uploading, ...current]);

      try {
        const uploaded = await uploadDocumentApi(
          request,
          file,
          publicConfig.demoKnowledgeBaseId,
        );
        setDocuments((current) =>
          current.map((document) =>
            document.localId === localId
              ? {
                  ...document,
                  id: uploaded.id,
                  jobId: uploaded.jobId,
                  fileName: uploaded.fileName,
                  status: uploaded.status,
                  uploadState: undefined,
                }
              : document,
          ),
        );
      } catch (error) {
        const message = error instanceof Error ? error.message : "Upload failed";
        toast.error(message);
        setDocuments((current) =>
          current.map((document) =>
            document.localId === localId
              ? {
                  ...document,
                  status: "FAILED",
                  uploadState: undefined,
                  errorMessage: message,
                }
              : document,
          ),
        );
      }
    }
  }

  function handleFileInputChange(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    if (files.length > 0) void uploadFiles(files);
    event.target.value = "";
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    const files = Array.from(event.dataTransfer.files ?? []);
    if (files.length > 0) void uploadFiles(files);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-800">Documents</h2>
        <Button
          className="gap-1.5 bg-indigo-600 text-white hover:bg-indigo-700"
          size="sm"
          onClick={() => fileInputRef.current?.click()}
        >
          <Upload className="h-4 w-4" />
          Upload Document
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept=".txt,.pdf,text/plain,application/pdf"
          className="hidden"
          onChange={handleFileInputChange}
        />
      </div>

      <div
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        className={`rounded-lg border-2 border-dashed p-10 text-center transition-colors ${
          isDragging
            ? "border-indigo-500 bg-indigo-50"
            : "border-slate-300 bg-slate-50 hover:border-indigo-400"
        }`}
      >
        <Cloud className="mx-auto mb-3 h-10 w-10 text-slate-400" />
        <p className="text-sm font-medium text-slate-700">Drop files here to upload</p>
        <p className="mt-1 text-xs text-slate-400">Supports TXT and text-based PDF up to 20 MB</p>
      </div>

      <Card>
        {loading ? (
          <TableSkeleton />
        ) : documents.length === 0 ? (
          <CardContent className="py-16 text-center">
            <FileText className="mx-auto mb-3 h-10 w-10 text-slate-300" />
            <p className="font-medium text-slate-500">No documents yet</p>
            <p className="mt-1 text-sm text-slate-400">
              Upload your first TXT or PDF document to start indexing
            </p>
          </CardContent>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>File Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Size</TableHead>
                <TableHead>Uploaded</TableHead>
                <TableHead>Details</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.map((doc) => (
                <TableRow key={doc.localId ?? doc.id}>
                  <TableCell className="font-medium">{doc.fileName}</TableCell>
                  <TableCell>
                    <Badge variant="secondary" className="text-xs uppercase">
                      {doc.fileType}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={doc.status} uploadState={doc.uploadState} />
                  </TableCell>
                  <TableCell className="text-sm text-slate-500">
                    {formatBytes(doc.fileSizeBytes)}
                  </TableCell>
                  <TableCell className="text-sm text-slate-500">
                    {formatDate(doc.uploadedAt)}
                  </TableCell>
                  <TableCell className="max-w-xs text-sm text-slate-500">
                    {doc.status === "COMPLETED" && doc.chunkCount
                      ? `${doc.chunkCount} chunks`
                      : doc.status === "FAILED"
                        ? doc.errorMessage ?? "Indexing failed"
                        : "Waiting for indexing"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}

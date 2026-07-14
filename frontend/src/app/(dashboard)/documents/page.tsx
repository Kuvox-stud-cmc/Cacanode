"use client";

import {
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
} from "react";
import toast from "react-hot-toast";
import { useRouter } from "next/navigation";
import type { Document, DocumentStatus, DocumentVisibility, TenantWorkspace } from "@/types";
import { useAuthStore } from "@/components/providers/StoreProvider";
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
import { Cloud, FileText, Loader2, Trash2, Upload } from "lucide-react";
import { useApiClient } from "@/hooks/useApiClient";
import {
  fileTypeFromName,
  deleteDocumentApi,
  getDocumentStatusApi,
  isSupportedDocumentName,
  isTerminalDocumentStatus,
  listDocumentsApi,
  uploadDocumentApi,
  updateDocumentVisibilityApi,
  SUPPORTED_DOCUMENT_ACCEPT,
} from "@/lib/documents-api";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { getTenantWorkspaceApi } from "@/lib/workspace-api";

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
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ${classes[status]}`}>
      {status === "PENDING" && (
        <span className="relative flex size-2" aria-hidden="true">
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-yellow-500 opacity-60" />
          <span className="relative inline-flex size-2 rounded-full bg-yellow-600" />
        </span>
      )}
      {status === "PROCESSING" && (
        <Loader2 className="size-3 animate-spin" aria-hidden="true" />
      )}
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
  return isSupportedDocumentName(file.name);
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
  const router = useRouter();
  const { request } = useApiClient();
  const user = useAuthStore((state) => state.user);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [documents, setDocuments] = useState<DashboardDocument[]>([]);
  const [workspace, setWorkspace] = useState<TenantWorkspace | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DashboardDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);
  const [visibility, setVisibility] = useState<DocumentVisibility>("EMPLOYEE_ONLY");

  useEffect(() => {
    let cancelled = false;
    getTenantWorkspaceApi(request)
      .then(async (tenantWorkspace) => {
        const items = await listDocumentsApi(request, tenantWorkspace.knowledgeBase.id);
        if (!cancelled) {
          setWorkspace(tenantWorkspace);
          setDocuments(items);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load workspace");
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
            visibility: result.value.visibility,
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

  async function uploadFiles(files: File[], selectedVisibility: DocumentVisibility) {
    if (!workspace) {
      toast.error("Workspace is still loading.");
      return;
    }

    const uploads = files.flatMap((file) => {
      if (!isSupportedFile(file)) {
        toast.error(`${file.name} is not supported. Use PDF, DOCX, TXT, Markdown, HTML, XLSX, or CSV.`);
        return [];
      }
      return [{ file, localId: makeId() }];
    });
    if (uploads.length === 0) return;

    const optimisticDocuments: DashboardDocument[] = uploads.map(({ file, localId }) => ({
      id: localId,
      localId,
      fileName: file.name,
      fileType: fileTypeFromName(file.name),
      status: "PENDING",
      uploadState: "UPLOADING",
      fileSizeBytes: file.size,
      jobId: localId,
      knowledgeBaseId: workspace.knowledgeBase.id,
      uploadedAt: new Date().toISOString(),
      visibility: selectedVisibility,
    }));
    setDocuments((current) => [...optimisticDocuments, ...current]);

    let nextUpload = 0;
    let successfulUploads = 0;
    const uploadNext = async () => {
      while (nextUpload < uploads.length) {
        const upload = uploads[nextUpload];
        nextUpload += 1;
        if (!upload) return;
        const { file, localId } = upload;

        try {
          const uploaded = await uploadDocumentApi(
            request,
            file,
            workspace.knowledgeBase.id,
            selectedVisibility,
          );
          successfulUploads += 1;
          setDocuments((current) =>
            current.map((document) =>
              document.localId === localId
                ? {
                    ...document,
                    id: uploaded.id,
                    jobId: uploaded.jobId,
                    fileName: uploaded.fileName,
                    status: uploaded.status,
                    visibility: uploaded.visibility,
                    uploadState: undefined,
                  }
                : document,
            ),
          );
        } catch (error) {
          const message = error instanceof Error ? error.message : "Upload failed";
          toast.error(`${file.name}: ${message}`);
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
    };

    const concurrency = Math.min(3, uploads.length);
    await Promise.all(Array.from({ length: concurrency }, () => uploadNext()));
    if (successfulUploads > 1) {
      toast.success(`${successfulUploads} documents uploaded`);
    }
  }

  function handleFileInputChange(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    if (files.length > 0) {
      setPendingFiles(files);
      setVisibility("EMPLOYEE_ONLY");
      setUploadOpen(true);
    }
    event.target.value = "";
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    const files = Array.from(event.dataTransfer.files ?? []);
    if (files.length > 0) {
      setPendingFiles(files);
      setVisibility("EMPLOYEE_ONLY");
      setUploadOpen(true);
    }
  }

  async function confirmUpload() {
    const files = pendingFiles;
    setUploadOpen(false);
    setPendingFiles([]);
    await uploadFiles(files, visibility);
  }

  async function changeVisibility(documentId: string, next: DocumentVisibility) {
    try {
      const updated = await updateDocumentVisibilityApi(request, documentId, next);
      setDocuments((current) => current.map((item) => item.id === documentId ? { ...item, visibility: updated.visibility } : item));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update visibility");
    }
  }

  async function confirmDelete() {
    const document = deleteTarget;
    if (!document) return;
    setDeletingId(document.id);
    try {
      await deleteDocumentApi(request, document.id);
      setDocuments((current) => current.filter((item) => item.id !== document.id));
      setDeleteTarget(null);
      toast.success(`${document.fileName} deleted`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to delete document");
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-800">Documents</h2>
        <Button
          className="gap-1.5 bg-indigo-600 text-white hover:bg-indigo-700"
          size="sm"
          disabled={!workspace || loading}
          onClick={() => fileInputRef.current?.click()}
        >
          <Upload className="h-4 w-4" />
          Upload Document
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept={SUPPORTED_DOCUMENT_ACCEPT}
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
        <p className="mt-1 text-xs text-slate-400">PDF, DOCX, TXT, Markdown, HTML, XLSX, and CSV up to 20 MB. Scanned PDFs and legacy Office files are excluded.</p>
      </div>

      <Card>
        {loading ? (
          <TableSkeleton />
        ) : documents.length === 0 ? (
          <CardContent className="py-16 text-center">
            <FileText className="mx-auto mb-3 h-10 w-10 text-slate-300" />
            <p className="font-medium text-slate-500">No documents yet</p>
            <p className="mt-1 text-sm text-slate-400">
              Upload your first digital document or spreadsheet to start indexing
            </p>
          </CardContent>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>File Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Access</TableHead>
                <TableHead>Size</TableHead>
                <TableHead>Uploaded</TableHead>
                <TableHead>Details</TableHead>
                {user?.role === "TENANT_ADMIN" && <TableHead className="w-16">Actions</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.map((doc) => (
                <TableRow
                  key={doc.localId ?? doc.id}
                  className={!doc.uploadState ? "cursor-pointer hover:bg-slate-50" : undefined}
                  tabIndex={!doc.uploadState ? 0 : undefined}
                  role={!doc.uploadState ? "link" : undefined}
                  onClick={() => {
                    if (!doc.uploadState) router.push(`/documents/${doc.id}`);
                  }}
                  onKeyDown={(event) => {
                    if (!doc.uploadState && (event.key === "Enter" || event.key === " ")) {
                      event.preventDefault();
                      router.push(`/documents/${doc.id}`);
                    }
                  }}
                >
                  <TableCell className="font-medium">{doc.fileName}</TableCell>
                  <TableCell>
                    <Badge variant="secondary" className="text-xs uppercase">
                      {doc.fileType}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={doc.status} uploadState={doc.uploadState} />
                  </TableCell>
                  <TableCell>
                    {user?.role === "TENANT_ADMIN" && !doc.uploadState ? (
                      <select
                        className="h-8 rounded-md border border-slate-200 bg-white px-2 text-xs"
                        value={doc.visibility}
                        onClick={(event) => event.stopPropagation()}
                        onKeyDown={(event) => event.stopPropagation()}
                        onChange={(event) => void changeVisibility(doc.id, event.target.value as DocumentVisibility)}
                      >
                        <option value="EMPLOYEE_ONLY">Employees only</option>
                        <option value="CUSTOMER_AND_EMPLOYEE">Everyone</option>
                      </select>
                    ) : (
                      <Badge variant="outline" className="whitespace-nowrap text-xs">
                        {doc.visibility === "CUSTOMER_AND_EMPLOYEE" ? "Everyone" : "Employees only"}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-sm text-slate-500">
                    {formatBytes(doc.fileSizeBytes)}
                  </TableCell>
                  <TableCell className="text-sm text-slate-500">
                    {formatDate(doc.uploadedAt)}
                  </TableCell>
                  <TableCell className="max-w-xs text-sm text-slate-500">
                    {doc.status === "COMPLETED"
                      ? "Ready to answer"
                      : doc.status === "FAILED"
                        ? doc.errorMessage ?? "Indexing failed"
                        : "Waiting for indexing"}
                  </TableCell>
                  {user?.role === "TENANT_ADMIN" && (
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-slate-400 hover:text-red-600"
                        aria-label={`Delete ${doc.fileName}`}
                        disabled={Boolean(doc.uploadState) || deletingId === doc.id || doc.status === "PENDING" || doc.status === "PROCESSING"}
                        onClick={(event) => {
                          event.stopPropagation();
                          setDeleteTarget(doc);
                        }}
                        onKeyDown={(event) => event.stopPropagation()}
                      >
                        {deletingId === doc.id ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />}
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Dialog open={uploadOpen} onOpenChange={setUploadOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Upload documents</DialogTitle>
            <DialogDescription>{pendingFiles.length} file{pendingFiles.length === 1 ? "" : "s"} selected. Choose who can use them in answers.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="max-h-40 space-y-1 overflow-y-auto rounded-lg bg-slate-50 p-3 text-sm">
              {pendingFiles.map((file) => <p key={`${file.name}-${file.size}`} className="truncate">{file.name}</p>)}
            </div>
            <div className="space-y-2">
              <Label htmlFor="document-visibility">Access</Label>
              <select id="document-visibility" className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm" value={visibility} onChange={(event) => setVisibility(event.target.value as DocumentVisibility)}>
                <option value="EMPLOYEE_ONLY">Employees only</option>
                {user?.role === "TENANT_ADMIN" && <option value="CUSTOMER_AND_EMPLOYEE">Customers and employees</option>}
              </select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUploadOpen(false)}>Cancel</Button>
            <Button onClick={() => void confirmUpload()}>Upload</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !deletingId) setDeleteTarget(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete document?</DialogTitle>
            <DialogDescription>
              This will permanently delete <span className="font-medium text-slate-700">{deleteTarget?.fileName}</span> and remove all of its indexed knowledge. This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              disabled={Boolean(deletingId)}
              onClick={() => setDeleteTarget(null)}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={Boolean(deletingId)}
              onClick={() => void confirmDelete()}
            >
              {deletingId ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />}
              Delete document
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

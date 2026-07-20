import type {
  Document,
  DocumentUnit,
  DocumentStatusResponse,
  DocumentUploadResponse,
  DocumentVisibility,
} from "@/types";
import { getApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";

export type ApiRequest = (endpoint: string, options?: RequestInit) => Promise<Response>;

export async function listDocumentsApi(
  request: ApiRequest,
  knowledgeBaseId: string,
): Promise<Document[]> {
  const params = new URLSearchParams({ knowledgeBaseId });
  const res = await request(`${getApiBase()}/documents?${params.toString()}`);
  return readJsonOrThrow<Document[]>(res);
}

export async function uploadDocumentApi(
  request: ApiRequest,
  file: File,
  knowledgeBaseId: string,
  visibility: DocumentVisibility,
): Promise<DocumentUploadResponse> {
  const form = new FormData();
  form.append("file", file);
  form.append("knowledgeBaseId", knowledgeBaseId);
  form.append("visibility", visibility);

  const res = await request(`${getApiBase()}/documents`, {
    method: "POST",
    body: form,
  });
  return readJsonOrThrow<DocumentUploadResponse>(res);
}

export async function updateDocumentVisibilityApi(
  request: ApiRequest,
  documentId: string,
  visibility: DocumentVisibility,
): Promise<DocumentStatusResponse> {
  const res = await request(`${getApiBase()}/documents/${documentId}/visibility`, {
    method: "PATCH",
    body: JSON.stringify({ visibility }),
  });
  return readJsonOrThrow<DocumentStatusResponse>(res);
}

export async function getDocumentStatusApi(
  request: ApiRequest,
  documentId: string,
): Promise<DocumentStatusResponse> {
  const res = await request(`${getApiBase()}/documents/${documentId}`);
  return readJsonOrThrow<DocumentStatusResponse>(res);
}

export async function getDocumentUnitsApi(
  request: ApiRequest,
  documentId: string,
): Promise<DocumentUnit[]> {
  const res = await request(`${getApiBase()}/documents/${documentId}/units`);
  return readJsonOrThrow<DocumentUnit[]>(res);
}

export async function downloadDocumentApi(
  request: ApiRequest,
  documentId: string,
): Promise<{ blob: Blob; fileName: string }> {
  const res = await request(`${getApiBase()}/documents/${documentId}/download`);
  if (!res.ok) await readJsonOrThrow<unknown>(res);
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const quoted = disposition.match(/filename="([^"]+)"/i)?.[1];
  const fileName = encoded ? decodeURIComponent(encoded) : quoted ?? "document";
  return { blob: await res.blob(), fileName };
}

export async function deleteDocumentApi(
  request: ApiRequest,
  documentId: string,
): Promise<void> {
  const res = await request(`${getApiBase()}/documents/${documentId}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    await readJsonOrThrow<unknown>(res);
  }
}

export function fileTypeFromName(name: string): string {
  const lower = name.toLowerCase();
  if (lower.endsWith(".pdf")) return "PDF";
  if (lower.endsWith(".txt")) return "TXT";
  if (lower.endsWith(".docx")) return "DOCX";
  if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "MARKDOWN";
  if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML";
  if (lower.endsWith(".xlsx")) return "XLSX";
  if (lower.endsWith(".csv")) return "CSV";
  return "UNKNOWN";
}

export const SUPPORTED_DOCUMENT_ACCEPT = [
  ".pdf", ".docx", ".txt", ".md", ".markdown", ".html", ".htm", ".xlsx", ".csv",
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "text/plain", "text/markdown", "text/html", "text/csv",
].join(",");

export function isSupportedDocumentName(name: string): boolean {
  return fileTypeFromName(name) !== "UNKNOWN";
}

export function isTerminalDocumentStatus(status: string): boolean {
  return status === "COMPLETED" || status === "FAILED";
}

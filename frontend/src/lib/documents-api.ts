import type {
  Document,
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

export function fileTypeFromName(name: string): string {
  const lower = name.toLowerCase();
  if (lower.endsWith(".pdf")) return "PDF";
  if (lower.endsWith(".txt")) return "TXT";
  return "UNKNOWN";
}

export function isTerminalDocumentStatus(status: string): boolean {
  return status === "COMPLETED" || status === "FAILED";
}

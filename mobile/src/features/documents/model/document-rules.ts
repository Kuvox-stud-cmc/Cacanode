import type { DocumentPickerAsset } from 'expo-document-picker';

import type {
  DocumentStatus,
  DocumentType,
  DocumentUploadQueueItem,
  SelectedDocumentFile,
} from '@/features/documents/types';
import type { ApiError } from '@/services/api/errors';

export const DOCUMENT_PAGE_SIZE = 20;
export const MAX_DOCUMENT_BATCH_SIZE = 10;
export const MAX_DOCUMENT_SIZE_BYTES = 20 * 1024 * 1024;

const TYPE_RULES: Record<DocumentType, { extensions: string[]; mimeTypes: string[] }> = {
  PDF: { extensions: ['pdf'], mimeTypes: ['application/pdf', 'application/octet-stream'] },
  DOCX: {
    extensions: ['docx'],
    mimeTypes: [
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/zip',
      'application/octet-stream',
    ],
  },
  TXT: { extensions: ['txt'], mimeTypes: ['text/plain', 'application/octet-stream'] },
  MARKDOWN: {
    extensions: ['md', 'markdown'],
    mimeTypes: ['text/markdown', 'text/plain', 'text/x-markdown', 'application/octet-stream'],
  },
  HTML: {
    extensions: ['html', 'htm'],
    mimeTypes: ['text/html', 'application/xhtml+xml', 'application/octet-stream'],
  },
  XLSX: {
    extensions: ['xlsx'],
    mimeTypes: [
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/zip',
      'application/octet-stream',
    ],
  },
  CSV: {
    extensions: ['csv'],
    mimeTypes: [
      'text/csv',
      'application/csv',
      'text/plain',
      'application/vnd.ms-excel',
      'application/octet-stream',
    ],
  },
};

const SAFE_SERVER_MESSAGES = new Set([
  'Uploaded file is empty',
  'Uploaded file exceeds 20 MB',
  'Unsupported file extension',
  'File extension and content type do not match',
  'Uploaded file could not be read',
  'PDF file signature is invalid',
  'Encrypted PDF files are not supported',
  'Office archive contains too many entries',
  'Office archive contains an unsafe path',
  'Office archive expands beyond the safety limit',
  'Office file container is malformed',
  'Office file container does not match its extension',
  'Text file must use UTF-8 encoding',
  'Text file contains invalid binary content',
  'Knowledge base is not active or not found',
  'Wait for document processing to finish before deleting it',
]);

export function documentTypeForName(fileName: string): DocumentType | null {
  const extension = fileName.includes('.') ? fileName.split('.').pop()?.toLowerCase() : undefined;
  if (!extension) return null;
  return (Object.entries(TYPE_RULES).find(([, rule]) => rule.extensions.includes(extension))?.[0]
    ?? null) as DocumentType | null;
}

export function validateSelectedDocument(asset: DocumentPickerAsset): string | null {
  if (!asset.size) return 'The selected file is empty.';
  if (asset.size > MAX_DOCUMENT_SIZE_BYTES) return 'Files must be 20 MiB or smaller.';
  const type = documentTypeForName(asset.name);
  if (!type) return 'Supported files are PDF, DOCX, TXT, Markdown, HTML, XLSX, and CSV.';
  const mimeType = asset.mimeType?.toLowerCase().split(';', 1)[0].trim() ?? '';
  if (!TYPE_RULES[type].mimeTypes.includes(mimeType)) {
    return 'The file extension and MIME type do not match.';
  }
  return null;
}

export function selectedFileKey(file: Pick<SelectedDocumentFile, 'name' | 'size' | 'mimeType'>) {
  return `${file.name}\u0000${file.size}\u0000${file.mimeType.toLowerCase()}`;
}

export function queuePickerAssets(
  assets: DocumentPickerAsset[],
  existing: DocumentUploadQueueItem[],
): DocumentUploadQueueItem[] {
  const existingKeys = new Set(existing.map(selectedFileKey));
  return assets.map((asset, index) => {
    const file: SelectedDocumentFile = {
      uri: asset.uri,
      name: asset.name,
      size: asset.size ?? 0,
      mimeType: asset.mimeType ?? '',
    };
    let errorMessage = validateSelectedDocument(asset);
    const key = selectedFileKey(file);
    if (!errorMessage && existingKeys.has(key)) errorMessage = 'This file is already in the batch.';
    if (!errorMessage && existing.length + index >= MAX_DOCUMENT_BATCH_SIZE) {
      errorMessage = 'A batch can contain at most 10 files.';
    }
    existingKeys.add(key);
    return {
      ...file,
      localId: `${Date.now()}-${index}-${Math.random().toString(36).slice(2)}`,
      status: errorMessage ? 'rejected' : 'ready',
      errorMessage,
      response: null,
    };
  });
}

export function isProcessingStatus(status: DocumentStatus) {
  return status === 'PENDING' || status === 'PROCESSING';
}

export function safeProcessingFailure(message: string | null | undefined) {
  if (message && SAFE_SERVER_MESSAGES.has(message)) return message;
  return 'Processing failed. Refresh the document or upload a corrected file.';
}

export function safeUploadError(error: ApiError) {
  if (error.kind === 'network' || error.kind === 'timeout') {
    return {
      ambiguous: true,
      message: 'The upload result is uncertain. Refresh Documents before resubmitting to avoid duplicates.',
    };
  }
  return {
    ambiguous: false,
    message: SAFE_SERVER_MESSAGES.has(error.message)
      ? error.message
      : 'The document could not be uploaded. Check the file and try again.',
  };
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
}

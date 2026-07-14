export const DOCUMENT_STATUSES = ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'] as const;
export const DOCUMENT_TYPES = ['PDF', 'DOCX', 'TXT', 'MARKDOWN', 'HTML', 'XLSX', 'CSV'] as const;
export const DOCUMENT_VISIBILITIES = ['EMPLOYEE_ONLY', 'CUSTOMER_AND_EMPLOYEE'] as const;

export type DocumentStatus = (typeof DOCUMENT_STATUSES)[number];
export type DocumentType = (typeof DOCUMENT_TYPES)[number];
export type DocumentVisibility = (typeof DOCUMENT_VISIBILITIES)[number];

export type DocumentListItem = {
  id: string;
  jobId: string;
  fileName: string;
  fileType: DocumentType;
  fileSizeBytes: number;
  knowledgeBaseId: string;
  status: DocumentStatus;
  visibility: DocumentVisibility;
  chunkCount: number | null;
  errorMessage: string | null;
  uploadedAt: string;
};

export type DocumentDetail = DocumentListItem;

export type DocumentUploadResponse = {
  id: string;
  jobId: string;
  fileName: string;
  status: DocumentStatus;
  visibility: DocumentVisibility;
};

export type DocumentFilters = {
  q?: string;
  status?: DocumentStatus;
  type?: DocumentType;
  visibility?: DocumentVisibility;
};

export type DocumentListRequest = DocumentFilters & {
  knowledgeBaseId: string;
  page: number;
  size?: number;
};

export type SelectedDocumentFile = {
  uri: string;
  name: string;
  size: number;
  mimeType: string;
};

export type UploadQueueStatus =
  | 'ready'
  | 'rejected'
  | 'uploading'
  | 'succeeded'
  | 'failed'
  | 'ambiguous';

export type DocumentUploadQueueItem = SelectedDocumentFile & {
  localId: string;
  status: UploadQueueStatus;
  errorMessage: string | null;
  response: DocumentUploadResponse | null;
};

export type TemporaryDocumentDownload = {
  uri: string;
  fileName: string;
  contentType: string;
};

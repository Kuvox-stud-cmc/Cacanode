import { filenameFromContentDisposition, writeTemporaryDownload } from '@/features/documents/services/document-files';
import type {
  DocumentDetail,
  DocumentListItem,
  DocumentListRequest,
  DocumentUploadResponse,
  DocumentVisibility,
  SelectedDocumentFile,
  TemporaryDocumentDownload,
} from '@/features/documents/types';
import type { ApiError } from '@/services/api/errors';
import { springApi } from '@/services/api/api';

export const DOCUMENT_TRANSFER_TIMEOUT_MS = 120_000;

type BinaryDocumentResponse = {
  blob: Blob;
  contentDisposition: string | null;
  contentType: string;
};

export const documentsApi = springApi.injectEndpoints({
  endpoints: (build) => ({
    listDocuments: build.query<DocumentListItem[], DocumentListRequest>({
      query: ({ knowledgeBaseId, page, size = 20, q, status, type, visibility }) => ({
        url: '/documents',
        params: {
          knowledgeBaseId,
          page,
          size,
          ...(q?.trim() ? { q: q.trim() } : {}),
          ...(status ? { status } : {}),
          ...(type ? { type } : {}),
          ...(visibility ? { visibility } : {}),
        },
      }),
      providesTags: (result) => [
        { type: 'Document', id: 'LIST' },
        ...(result ?? []).map((document) => ({ type: 'Document' as const, id: document.id })),
      ],
    }),
    getDocument: build.query<DocumentDetail, string>({
      query: (documentId) => `/documents/${documentId}`,
      providesTags: (_result, _error, documentId) => [{ type: 'Document', id: documentId }],
    }),
    uploadDocument: build.mutation<
      DocumentUploadResponse,
      { file: SelectedDocumentFile; knowledgeBaseId: string; visibility: DocumentVisibility }
    >({
      query: ({ file, knowledgeBaseId, visibility }) => {
        const body = new FormData();
        body.append('file', {
          uri: file.uri,
          name: file.name,
          type: file.mimeType,
        } as unknown as Blob);
        body.append('knowledgeBaseId', knowledgeBaseId);
        body.append('visibility', visibility);
        return {
          url: '/documents',
          method: 'POST',
          body,
          timeout: DOCUMENT_TRANSFER_TIMEOUT_MS,
        };
      },
      invalidatesTags: [
        { type: 'Document', id: 'LIST' },
        { type: 'Dashboard' },
      ],
    }),
    updateDocumentVisibility: build.mutation<
      DocumentDetail,
      { documentId: string; visibility: DocumentVisibility }
    >({
      query: ({ documentId, visibility }) => ({
        url: `/documents/${documentId}/visibility`,
        method: 'PATCH',
        body: { visibility },
      }),
      invalidatesTags: (_result, _error, { documentId }) => [
        { type: 'Document', id: documentId },
        { type: 'Document', id: 'LIST' },
        { type: 'Dashboard' },
      ],
    }),
    deleteDocument: build.mutation<void, string>({
      query: (documentId) => ({ url: `/documents/${documentId}`, method: 'DELETE' }),
      transformResponse: () => undefined,
      invalidatesTags: (_result, _error, documentId) => [
        { type: 'Document', id: documentId },
        { type: 'Document', id: 'LIST' },
        { type: 'Dashboard' },
      ],
    }),
    downloadDocument: build.mutation<
      TemporaryDocumentDownload,
      { documentId: string; fallbackFileName: string }
    >({
      async queryFn({ documentId, fallbackFileName }, _api, _extraOptions, baseQuery) {
        const result = await baseQuery({
          url: `/documents/${documentId}/download`,
          timeout: DOCUMENT_TRANSFER_TIMEOUT_MS,
          responseHandler: async (response) => ({
            blob: await response.blob(),
            contentDisposition: response.headers.get('Content-Disposition'),
            contentType: response.headers.get('Content-Type') ?? 'application/octet-stream',
          }),
        });
        if (result.error) return { error: result.error as ApiError };
        const binary = result.data as BinaryDocumentResponse;
        try {
          const fileName = filenameFromContentDisposition(
            binary.contentDisposition,
            fallbackFileName,
          );
          return {
            data: await writeTemporaryDownload(binary.blob, fileName, binary.contentType),
          };
        } catch {
          return {
            error: {
              kind: 'unknown',
              status: null,
              code: null,
              message: 'The downloaded file could not be prepared for sharing.',
              messages: [],
              requestId: null,
            } satisfies ApiError,
          };
        }
      },
    }),
  }),
});

export const {
  useDeleteDocumentMutation,
  useDownloadDocumentMutation,
  useGetDocumentQuery,
  useLazyListDocumentsQuery,
  useListDocumentsQuery,
  useUpdateDocumentVisibilityMutation,
  useUploadDocumentMutation,
} = documentsApi;

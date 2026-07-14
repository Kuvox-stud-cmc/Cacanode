import { documentsApi, DOCUMENT_TRANSFER_TIMEOUT_MS } from '@/features/documents/api/documents-api';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { springApi } from '@/services/api/api';
import { createAppStore } from '@/store';

const mockWriteDownload = jest.fn();
jest.mock('@/features/documents/services/document-files', () => ({
  filenameFromContentDisposition: (_header: string | null, fallback: string) => fallback,
  writeTemporaryDownload: (...args: unknown[]) => mockWriteDownload(...args),
}));

function jsonResponse(body: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: status === 204 ? undefined : { 'Content-Type': 'application/json' },
  });
}

const document = {
  id: 'document-1', jobId: 'job-1', fileName: 'policy.pdf', fileType: 'PDF',
  fileSizeBytes: 100, knowledgeBaseId: 'kb-1', status: 'COMPLETED',
  visibility: 'EMPLOYEE_ONLY', chunkCount: 3, errorMessage: null,
  uploadedAt: '2026-07-14T08:00:00',
} as const;

describe('documents API', () => {
  beforeAll(() => jest.useFakeTimers());
  beforeEach(() => {
    jest.restoreAllMocks();
    accessTokenStore.set('access-token');
    mockWriteDownload.mockResolvedValue({ uri: 'file:///cache/policy.pdf', fileName: 'policy.pdf', contentType: 'application/pdf' });
  });
  afterEach(() => {
    accessTokenStore.set(null);
    jest.clearAllTimers();
  });
  afterAll(() => jest.useRealTimers());

  it('sends paging and combined document filters without tenant identity', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([document]));
    const store = createAppStore();
    const query = store.dispatch(documentsApi.endpoints.listDocuments.initiate({
      knowledgeBaseId: 'kb-1', page: 2, size: 20, q: ' policy ', status: 'COMPLETED',
      type: 'PDF', visibility: 'EMPLOYEE_ONLY',
    }));
    await expect(query.unwrap()).resolves.toEqual([document]);
    const request = fetchMock.mock.calls[0][0] as Request;
    const url = new URL(request.url);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      knowledgeBaseId: 'kb-1', page: '2', size: '20', q: 'policy', status: 'COMPLETED',
      type: 'PDF', visibility: 'EMPLOYEE_ONLY',
    });
    expect(url.searchParams.has('tenantId')).toBe(false);
    query.unsubscribe();
    store.dispatch(springApi.util.resetApiState());
  });

  it('uses exact multipart field names and invalidates list/dashboard tags', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      id: 'document-2', jobId: 'job-2', fileName: 'notes.txt', status: 'PENDING', visibility: 'EMPLOYEE_ONLY',
    }, 202));
    const store = createAppStore();
    const upload = store.dispatch(documentsApi.endpoints.uploadDocument.initiate({
      knowledgeBaseId: 'kb-1', visibility: 'EMPLOYEE_ONLY',
      file: { uri: 'file:///cache/notes.txt', name: 'notes.txt', size: 5, mimeType: 'text/plain' },
    }));
    await upload.unwrap();
    const request = fetchMock.mock.calls[0][0] as Request;
    const form = await request.formData() as unknown as {
      get(name: string): unknown;
      keys(): IterableIterator<string>;
    };
    expect([...form.keys()].sort()).toEqual(['file', 'knowledgeBaseId', 'visibility']);
    expect(form.get('knowledgeBaseId')).toBe('kb-1');
    expect(form.get('visibility')).toBe('EMPLOYEE_ONLY');
    upload.reset();
    store.dispatch(springApi.util.resetApiState());
  });

  it('downloads authenticated binary content and prepares a serializable cache result', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('pdf-data', {
      status: 200,
      headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="server.pdf"' },
    }));
    const store = createAppStore();
    const download = store.dispatch(documentsApi.endpoints.downloadDocument.initiate({
      documentId: 'document-1', fallbackFileName: 'policy.pdf',
    }));
    await expect(download.unwrap()).resolves.toEqual({
      uri: 'file:///cache/policy.pdf', fileName: 'policy.pdf', contentType: 'application/pdf',
    });
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.headers.get('Authorization')).toBe('Bearer access-token');
    expect(mockWriteDownload).toHaveBeenCalledWith(expect.any(Blob), 'policy.pdf', 'application/pdf');
    download.reset();
    store.dispatch(springApi.util.resetApiState());
  });

  it('times out transfers at 120 seconds and never retries mutations', async () => {
    let signal: AbortSignal | undefined;
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request;
      signal = request.signal;
      return await new Promise<Response>((_resolve, reject) => {
        request.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      });
    });
    const store = createAppStore();
    const upload = store.dispatch(documentsApi.endpoints.uploadDocument.initiate({
      knowledgeBaseId: 'kb-1', visibility: 'EMPLOYEE_ONLY',
      file: { uri: 'file:///cache/notes.txt', name: 'notes.txt', size: 5, mimeType: 'text/plain' },
    }));
    await jest.advanceTimersByTimeAsync(DOCUMENT_TRANSFER_TIMEOUT_MS - 1);
    expect(signal?.aborted).toBe(false);
    await jest.advanceTimersByTimeAsync(1);
    expect(signal?.aborted).toBe(true);
    await expect(upload.unwrap()).rejects.toMatchObject({ kind: 'network' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    upload.reset();
    store.dispatch(springApi.util.resetApiState());
  });
});

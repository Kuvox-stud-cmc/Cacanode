import {
  MAX_DOCUMENT_BATCH_SIZE,
  MAX_DOCUMENT_SIZE_BYTES,
  queuePickerAssets,
  safeProcessingFailure,
  safeUploadError,
  validateSelectedDocument,
} from '@/features/documents/model/document-rules';
import {
  filtersFromRoute,
  filtersToRoute,
  mergeDocumentPages,
  runWithConcurrency,
  shouldPollFirstDocumentPage,
} from '@/features/documents/model/document-list-state';
import type { DocumentPickerAsset } from 'expo-document-picker';
import type { DocumentListItem, DocumentUploadQueueItem } from '@/features/documents/types';

function asset(overrides: Partial<DocumentPickerAsset> = {}): DocumentPickerAsset {
  return {
    name: 'policy.pdf',
    size: 100,
    uri: 'file:///cache/policy.pdf',
    mimeType: 'application/pdf',
    lastModified: 0,
    ...overrides,
  };
}

describe('document selection and queue rules', () => {
  it('validates empty, oversized, unsupported, and MIME-mismatched files', () => {
    expect(validateSelectedDocument(asset({ size: 0 }))).toContain('empty');
    expect(validateSelectedDocument(asset({ size: MAX_DOCUMENT_SIZE_BYTES + 1 }))).toContain('20 MiB');
    expect(validateSelectedDocument(asset({ name: 'legacy.doc', mimeType: 'application/msword' }))).toContain('Supported');
    expect(validateSelectedDocument(asset({ mimeType: 'text/plain' }))).toContain('MIME');
    expect(validateSelectedDocument(asset())).toBeNull();
  });

  it('retains duplicate and over-limit selections as rejected queue rows', () => {
    const existing = queuePickerAssets([asset()], []);
    const duplicates = queuePickerAssets([asset({ uri: 'file:///cache/copy.pdf' })], existing);
    expect(duplicates[0]).toMatchObject({ status: 'rejected', errorMessage: 'This file is already in the batch.' });

    const full = Array.from({ length: MAX_DOCUMENT_BATCH_SIZE }, (_, index) => ({
      ...existing[0],
      localId: String(index),
      name: `file-${index}.pdf`,
    })) as DocumentUploadQueueItem[];
    expect(queuePickerAssets([asset({ name: 'extra.pdf' })], full)[0]).toMatchObject({
      status: 'rejected',
      errorMessage: 'A batch can contain at most 10 files.',
    });
  });

  it('runs uploads with at most two active tasks and preserves partial completion', async () => {
    let active = 0;
    let maximum = 0;
    const completed: number[] = [];
    const releases: (() => void)[] = [];
    const running = runWithConcurrency([1, 2, 3, 4], 2, async (value) => {
      active += 1;
      maximum = Math.max(maximum, active);
      await new Promise<void>((resolve) => releases.push(resolve));
      completed.push(value);
      active -= 1;
    });

    await Promise.resolve();
    expect(active).toBe(2);
    releases.shift()?.();
    await Promise.resolve();
    await Promise.resolve();
    expect(active).toBe(2);
    while (releases.length) {
      releases.shift()?.();
      await Promise.resolve();
      await Promise.resolve();
    }
    await running;
    expect(maximum).toBe(2);
    expect(completed).toHaveLength(4);
  });

  it('marks network ambiguity and hides unsafe processing internals', () => {
    expect(safeUploadError({
      kind: 'timeout', status: null, code: null, message: 'raw', messages: [], requestId: null,
    })).toMatchObject({ ambiguous: true });
    expect(safeProcessingFailure('provider bucket secret')).not.toContain('provider');
    expect(safeProcessingFailure('Encrypted PDF files are not supported')).toBe('Encrypted PDF files are not supported');
  });
});

describe('document pagination state', () => {
  it('merges incremental pages without duplicate IDs', () => {
    const item = (id: string) => ({ id } as DocumentListItem);
    expect(mergeDocumentPages([[item('1'), item('2')], [item('2'), item('3')]]).map(({ id }) => id))
      .toEqual(['1', '2', '3']);
  });

  it('restores valid filters and pauses first-page polling in background or after pagination', () => {
    expect(filtersFromRoute({
      q: ' policy ', status: 'PROCESSING', type: 'PDF', visibility: 'EMPLOYEE_ONLY',
    })).toEqual({ q: 'policy', status: 'PROCESSING', type: 'PDF', visibility: 'EMPLOYEE_ONLY' });
    expect(filtersToRoute({ status: 'FAILED' })).toEqual({
      q: undefined, status: 'FAILED', type: undefined, visibility: undefined,
    });
    const processing = [{ status: 'PROCESSING' } as DocumentListItem];
    expect(shouldPollFirstDocumentPage({ appActive: true, documents: processing, extraPageCount: 0, focused: true })).toBe(true);
    expect(shouldPollFirstDocumentPage({ appActive: false, documents: processing, extraPageCount: 0, focused: true })).toBe(false);
    expect(shouldPollFirstDocumentPage({ appActive: true, documents: processing, extraPageCount: 1, focused: true })).toBe(false);
  });
});

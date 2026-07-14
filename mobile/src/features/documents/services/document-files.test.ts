import {
  filenameFromContentDisposition,
  shareTemporaryDownload,
  writeTemporaryDownload,
} from '@/features/documents/services/document-files';

const mockFiles = new Map<string, { bytes?: Uint8Array; deleted?: boolean }>();
const mockShare = jest.fn();
const mockAvailable = jest.fn();

jest.mock('expo-file-system', () => ({
  Paths: { cache: { uri: 'file:///cache/' } },
  File: class MockFile {
    uri: string;
    constructor(...parts: (string | { uri: string })[]) {
      this.uri = parts.map((part) => typeof part === 'string' ? part : part.uri).join('');
    }
    get exists() { return mockFiles.has(this.uri) && !mockFiles.get(this.uri)?.deleted; }
    create() { mockFiles.set(this.uri, {}); }
    write(bytes: Uint8Array) { mockFiles.set(this.uri, { bytes }); }
    delete() { mockFiles.set(this.uri, { ...mockFiles.get(this.uri), deleted: true }); }
  },
}));

jest.mock('expo-sharing', () => ({
  isAvailableAsync: () => mockAvailable(),
  shareAsync: (...args: unknown[]) => mockShare(...args),
}));

describe('document temporary files', () => {
  beforeEach(() => {
    mockFiles.clear();
    jest.clearAllMocks();
    mockAvailable.mockResolvedValue(true);
    mockShare.mockResolvedValue(undefined);
  });

  it('parses UTF-8 and quoted download filenames safely', () => {
    expect(filenameFromContentDisposition("attachment; filename*=UTF-8''policy%20vi.pdf", 'fallback.pdf'))
      .toBe('policy vi.pdf');
    expect(filenameFromContentDisposition('attachment; filename="notes.txt"', 'fallback.txt'))
      .toBe('notes.txt');
    expect(filenameFromContentDisposition(null, '../fallback.txt')).toBe('.._fallback.txt');
  });

  it('writes downloads to cache and deletes them after successful sharing', async () => {
    const download = await writeTemporaryDownload(new Blob(['hello']), 'notes.txt', 'text/plain');
    expect(mockFiles.get(download.uri)?.bytes).toEqual(new Uint8Array([104, 101, 108, 108, 111]));
    await shareTemporaryDownload(download);
    expect(mockShare).toHaveBeenCalledWith(download.uri, expect.objectContaining({ mimeType: 'text/plain' }));
    expect(mockFiles.get(download.uri)?.deleted).toBe(true);
  });

  it('deletes the cache file when native sharing fails', async () => {
    const download = await writeTemporaryDownload(new Blob(['hello']), 'notes.txt', 'text/plain');
    mockShare.mockRejectedValue(new Error('share failed'));
    await expect(shareTemporaryDownload(download)).rejects.toThrow('share failed');
    expect(mockFiles.get(download.uri)?.deleted).toBe(true);
  });
});

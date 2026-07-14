import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import type { TemporaryDocumentDownload } from '@/features/documents/types';

function safeFileName(value: string) {
  const sanitized = value.replace(/[\\/:*?"<>|\u0000-\u001F]/g, '_').trim();
  return sanitized || 'document';
}

export function filenameFromContentDisposition(header: string | null, fallback: string) {
  if (!header) return safeFileName(fallback);
  const encoded = header.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    try {
      return safeFileName(decodeURIComponent(encoded));
    } catch {
      // Fall through to the plain filename parameter.
    }
  }
  const quoted = header.match(/filename="([^"]+)"/i)?.[1];
  const plain = quoted ?? header.match(/filename=([^;]+)/i)?.[1]?.trim();
  return safeFileName(plain ?? fallback);
}

export async function writeTemporaryDownload(
  blob: Blob,
  fileName: string,
  contentType: string,
): Promise<TemporaryDocumentDownload> {
  const file = new File(
    Paths.cache,
    `cacanode-${Date.now()}-${Math.random().toString(36).slice(2)}-${safeFileName(fileName)}`,
  );
  file.create({ overwrite: true });
  file.write(new Uint8Array(await blob.arrayBuffer()));
  return { uri: file.uri, fileName, contentType };
}

export function deleteTemporaryFile(uri: string) {
  try {
    const file = new File(uri);
    if (file.exists) file.delete();
  } catch {
    // Temporary cache cleanup is best effort.
  }
}

export function deletePickerCopy(uri: string) {
  deleteTemporaryFile(uri);
}

export async function shareTemporaryDownload(download: TemporaryDocumentDownload) {
  try {
    if (!(await Sharing.isAvailableAsync())) {
      throw new Error('Sharing is not available on this device.');
    }
    await Sharing.shareAsync(download.uri, {
      dialogTitle: download.fileName,
      mimeType: download.contentType,
    });
  } finally {
    deleteTemporaryFile(download.uri);
  }
}

import {
  DOCUMENT_STATUSES,
  DOCUMENT_TYPES,
  DOCUMENT_VISIBILITIES,
  type DocumentFilters,
  type DocumentListItem,
  type DocumentStatus,
  type DocumentType,
  type DocumentVisibility,
} from '@/features/documents/types';
import { isProcessingStatus } from '@/features/documents/model/document-rules';

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function allowed<T extends string>(value: string | undefined, values: readonly T[]) {
  return value && values.includes(value as T) ? (value as T) : undefined;
}

export function filtersFromRoute(params: Record<string, string | string[] | undefined>): DocumentFilters {
  const query = first(params.q)?.trim().slice(0, 200);
  return {
    ...(query ? { q: query } : {}),
    ...(allowed(first(params.status), DOCUMENT_STATUSES)
      ? { status: allowed(first(params.status), DOCUMENT_STATUSES) as DocumentStatus }
      : {}),
    ...(allowed(first(params.type), DOCUMENT_TYPES)
      ? { type: allowed(first(params.type), DOCUMENT_TYPES) as DocumentType }
      : {}),
    ...(allowed(first(params.visibility), DOCUMENT_VISIBILITIES)
      ? { visibility: allowed(first(params.visibility), DOCUMENT_VISIBILITIES) as DocumentVisibility }
      : {}),
  };
}

export function filtersToRoute(filters: DocumentFilters) {
  return {
    q: filters.q || undefined,
    status: filters.status || undefined,
    type: filters.type || undefined,
    visibility: filters.visibility || undefined,
  };
}

export function mergeDocumentPages(pages: DocumentListItem[][]) {
  const seen = new Set<string>();
  return pages.flatMap((page) => page.filter((document) => {
    if (seen.has(document.id)) return false;
    seen.add(document.id);
    return true;
  }));
}

export function shouldPollFirstDocumentPage({
  appActive,
  documents,
  extraPageCount,
  focused,
}: {
  appActive: boolean;
  documents: DocumentListItem[];
  extraPageCount: number;
  focused: boolean;
}) {
  return focused
    && appActive
    && extraPageCount === 0
    && documents.some((document) => isProcessingStatus(document.status));
}

export async function runWithConcurrency<T>(
  items: T[],
  concurrency: number,
  task: (item: T) => Promise<void>,
) {
  let nextIndex = 0;
  async function worker() {
    while (nextIndex < items.length) {
      const item = items[nextIndex];
      nextIndex += 1;
      await task(item);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker));
}

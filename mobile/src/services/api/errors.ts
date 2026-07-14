import type { FetchBaseQueryError } from '@reduxjs/toolkit/query';

export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'parsing' | 'unknown';

export type ApiError = {
  kind: ApiErrorKind;
  status: number | null;
  code: string | null;
  message: string;
  messages: string[];
  requestId: string | null;
  details?: Record<string, unknown>;
};

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringsFromUnknown(value: unknown): string[] {
  if (typeof value === 'string' && value.trim()) return [value];
  if (Array.isArray(value)) {
    return value
      .map((item) => {
        if (typeof item === 'string') return item;
        if (isRecord(item) && typeof item.msg === 'string') return item.msg;
        return null;
      })
      .filter((item): item is string => Boolean(item));
  }
  return [];
}

function normalizeHttpError(status: number, data: unknown): ApiError {
  if (isRecord(data) && isRecord(data.error)) {
    const messages = stringsFromUnknown(data.error.message);
    return {
      kind: 'http',
      status,
      code: typeof data.error.code === 'string' ? data.error.code : null,
      message: messages[0] ?? `Request failed with status ${status}`,
      messages,
      requestId: typeof data.error.request_id === 'string' ? data.error.request_id : null,
      details: isRecord(data.error.details) ? data.error.details : undefined,
    };
  }

  if (isRecord(data)) {
    const messages = stringsFromUnknown(data.message);
    const detailMessages = stringsFromUnknown(data.detail);
    const combined = messages.length ? messages : detailMessages;

    return {
      kind: 'http',
      status,
      code: null,
      message: combined[0] ?? `Request failed with status ${status}`,
      messages: combined,
      requestId: null,
    };
  }

  return {
    kind: 'http',
    status,
    code: null,
    message: `Request failed with status ${status}`,
    messages: [],
    requestId: null,
  };
}

export function normalizeApiError(error: FetchBaseQueryError): ApiError {
  if (typeof error.status === 'number') {
    return normalizeHttpError(error.status, error.data);
  }

  if (error.status === 'FETCH_ERROR') {
    return {
      kind: 'network',
      status: null,
      code: null,
      message: 'Unable to reach the service. Check your connection and try again.',
      messages: [],
      requestId: null,
    };
  }

  if (error.status === 'TIMEOUT_ERROR') {
    return {
      kind: 'timeout',
      status: null,
      code: null,
      message: 'The request took too long. Please try again.',
      messages: [],
      requestId: null,
    };
  }

  if (error.status === 'PARSING_ERROR') {
    return {
      kind: 'parsing',
      status: error.originalStatus,
      code: null,
      message: 'The service returned an unreadable response.',
      messages: [],
      requestId: null,
    };
  }

  return {
    kind: 'unknown',
    status: null,
    code: null,
    message: 'An unexpected request error occurred.',
    messages: [],
    requestId: null,
  };
}

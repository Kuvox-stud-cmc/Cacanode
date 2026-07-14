import { normalizeApiError } from '@/services/api/errors';

describe('normalizeApiError', () => {
  it('normalizes Spring validation messages', () => {
    expect(
      normalizeApiError({
        status: 400,
        data: { message: ['Email is required', 'Password is required'] },
      }),
    ).toMatchObject({
      kind: 'http',
      status: 400,
      message: 'Email is required',
      messages: ['Email is required', 'Password is required'],
    });
  });

  it('normalizes FastAPI error envelopes', () => {
    expect(
      normalizeApiError({
        status: 429,
        data: {
          error: {
            code: 'MESSAGE_QUOTA_EXCEEDED',
            message: 'The tenant message quota has been reached.',
            request_id: 'request-123',
            details: { limit: 100 },
          },
        },
      }),
    ).toEqual({
      kind: 'http',
      status: 429,
      code: 'MESSAGE_QUOTA_EXCEEDED',
      message: 'The tenant message quota has been reached.',
      messages: ['The tenant message quota has been reached.'],
      requestId: 'request-123',
      details: { limit: 100 },
    });
  });

  it('normalizes default FastAPI validation details', () => {
    expect(
      normalizeApiError({
        status: 422,
        data: { detail: [{ msg: 'Field required' }, { msg: 'Invalid email' }] },
      }),
    ).toMatchObject({
      message: 'Field required',
      messages: ['Field required', 'Invalid email'],
    });
  });

  it('uses safe messages for transport and parsing failures', () => {
    expect(normalizeApiError({ status: 'FETCH_ERROR', error: 'socket details' })).toMatchObject({
      kind: 'network',
      status: null,
    });
    expect(
      normalizeApiError({
        status: 'PARSING_ERROR',
        originalStatus: 502,
        data: '<html>',
        error: 'parse details',
      }),
    ).toMatchObject({ kind: 'parsing', status: 502 });
  });
});

import type { BaseQueryApi } from '@reduxjs/toolkit/query';

import { createApiBaseQuery } from '@/services/api/base-query';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { tokenVault } from '@/services/auth/token-vault';
import { createAppStore } from '@/store';

jest.mock('@/services/auth/token-vault', () => ({
  tokenVault: { get: jest.fn(), set: jest.fn(), clear: jest.fn() },
}));

const credentials = {
  accessToken: 'new-access',
  refreshToken: 'new-refresh',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    userId: 'user-1',
    tenantId: 'tenant-1',
    email: 'person@example.com',
    fullName: 'Person Name',
    role: 'TENANT_ADMIN',
    plan: 'PRO',
  },
};

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function baseQueryApi(store: ReturnType<typeof createAppStore>): BaseQueryApi {
  return {
    signal: new AbortController().signal,
    abort: jest.fn(),
    dispatch: store.dispatch,
    getState: store.getState,
    extra: undefined,
    endpoint: 'test',
    type: 'query',
    forced: false,
    queryCacheKey: 'test',
  };
}

describe('authenticated base query', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    accessTokenStore.set('old-access');
    jest.mocked(tokenVault.get).mockResolvedValue('stored-refresh');
    jest.mocked(tokenVault.set).mockResolvedValue();
    jest.mocked(tokenVault.clear).mockResolvedValue();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('shares one refresh across simultaneous Spring and FastAPI 401 responses', async () => {
    let refreshCalls = 0;
    jest.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const request = input as Request;
      if (request.url.endsWith('/auth/mobile/refresh')) {
        refreshCalls += 1;
        await Promise.resolve();
        return jsonResponse(200, credentials);
      }
      if (request.headers.get('Authorization') === 'Bearer new-access') {
        return jsonResponse(200, { ok: true });
      }
      return jsonResponse(401, { message: 'Unauthorized' });
    });

    const store = createAppStore();
    const api = baseQueryApi(store);
    const [springResult, aiResult] = await Promise.all([
      createApiBaseQuery('http://localhost:8080/api/v1')('/dashboard', api, {}),
      createApiBaseQuery('http://localhost:8000/api/v1')('/chat', api, {}),
    ]);

    expect(refreshCalls).toBe(1);
    expect(springResult).toEqual({ data: { ok: true } });
    expect(aiResult).toEqual({ data: { ok: true } });
    expect(accessTokenStore.get()).toBe('new-access');
    expect(tokenVault.set).toHaveBeenCalledWith('new-refresh');
  });

  it('never refreshes authentication endpoints', async () => {
    const fetchMock = jest
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(401, { message: 'Invalid credentials' }));
    const store = createAppStore();

    const result = await createApiBaseQuery('http://localhost:8080/api/v1')(
      { url: '/auth/mobile/login', method: 'POST', body: {} },
      baseQueryApi(store),
      {},
    );

    expect(result.error).toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(tokenVault.get).not.toHaveBeenCalled();
  });

  it('rejects every waiter and clears the session after terminal refresh failure', async () => {
    let refreshCalls = 0;
    jest.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const request = input as Request;
      if (request.url.endsWith('/auth/mobile/refresh')) {
        refreshCalls += 1;
        return jsonResponse(401, { message: 'Invalid refresh token' });
      }
      return jsonResponse(401, { message: 'Unauthorized' });
    });
    const store = createAppStore();
    const query = createApiBaseQuery('http://localhost:8080/api/v1');
    const api = baseQueryApi(store);

    const results = await Promise.all([query('/one', api, {}), query('/two', api, {})]);

    expect(refreshCalls).toBe(1);
    expect(results[0].error).toMatchObject({ status: 401 });
    expect(results[1].error).toMatchObject({ status: 401 });
    expect(tokenVault.clear).toHaveBeenCalled();
    expect(accessTokenStore.get()).toBeNull();
    expect(store.getState().auth.status).toBe('unauthenticated');
  });

  it('retains credentials and returns a normalized network error when refresh is transient', async () => {
    let refreshCalls = 0;
    jest.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const request = input as Request;
      if (request.url.endsWith('/auth/mobile/refresh')) {
        refreshCalls += 1;
        throw new TypeError('network details');
      }
      return jsonResponse(401, { message: 'Unauthorized' });
    });
    const store = createAppStore();
    const query = createApiBaseQuery('http://localhost:8080/api/v1');
    const api = baseQueryApi(store);

    const results = await Promise.all([query('/one', api, {}), query('/two', api, {})]);

    expect(refreshCalls).toBe(1);
    expect(results[0].error).toMatchObject({ kind: 'network' });
    expect(results[1].error).toMatchObject({ kind: 'network' });
    expect(tokenVault.clear).not.toHaveBeenCalled();
    expect(accessTokenStore.get()).toBe('old-access');
  });

  it('clears the session after a retried request returns a second 401', async () => {
    jest.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const request = input as Request;
      if (request.url.endsWith('/auth/mobile/refresh')) return jsonResponse(200, credentials);
      return jsonResponse(401, { message: 'Unauthorized' });
    });
    const store = createAppStore();

    const result = await createApiBaseQuery('http://localhost:8080/api/v1')(
      '/protected',
      baseQueryApi(store),
      {},
    );

    expect(result.error).toMatchObject({ status: 401 });
    expect(tokenVault.clear).toHaveBeenCalled();
    expect(accessTokenStore.get()).toBeNull();
  });
});

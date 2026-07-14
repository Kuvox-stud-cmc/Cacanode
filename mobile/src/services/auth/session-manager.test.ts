import { createAppStore } from '@/store';
import { accessTokenStore } from '@/services/auth/access-token-store';
import {
  bootstrapSession,
  clearLocalSession,
  commitSession,
} from '@/services/auth/session-manager';
import { tokenVault } from '@/services/auth/token-vault';
import { chatApi } from '@/features/chat/api/chat-api';
import { workspaceApi } from '@/features/chat/api/workspace-api';

jest.mock('@/services/auth/token-vault', () => ({
  tokenVault: { get: jest.fn(), set: jest.fn(), clear: jest.fn() },
}));

const credentials = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
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

describe('session manager', () => {
  beforeAll(() => {
    jest.useFakeTimers();
  });

  beforeEach(() => {
    jest.clearAllMocks();
    accessTokenStore.set(null);
    jest.mocked(tokenVault.clear).mockResolvedValue();
    jest.mocked(tokenVault.set).mockResolvedValue();
  });

  afterEach(() => {
    jest.clearAllTimers();
  });

  afterAll(() => {
    jest.useRealTimers();
  });

  it('persists the rotated refresh token before publishing the session', async () => {
    const store = createAppStore();
    jest.mocked(tokenVault.set).mockImplementation(async () => {
      expect(accessTokenStore.get()).toBeNull();
      expect(store.getState().auth.status).toBe('bootstrapping');
    });

    await commitSession(credentials, store.dispatch);

    expect(accessTokenStore.get()).toBe('access-token');
    expect(store.getState().auth).toMatchObject({ status: 'authenticated', user: credentials.user });
  });

  it('rolls back the full local session when secure persistence fails', async () => {
    const store = createAppStore();
    jest.mocked(tokenVault.set).mockRejectedValue(new Error('vault unavailable'));

    await expect(commitSession(credentials, store.dispatch)).rejects.toThrow('vault unavailable');
    expect(accessTokenStore.get()).toBeNull();
    expect(store.getState().auth.status).toBe('unauthenticated');
    expect(tokenVault.clear).toHaveBeenCalled();
  });

  it('routes missing tokens to login and restores valid sessions', async () => {
    const noTokenStore = createAppStore();
    jest.mocked(tokenVault.get).mockResolvedValueOnce(null);
    await bootstrapSession(noTokenStore.dispatch, jest.fn());
    expect(noTokenStore.getState().auth.status).toBe('unauthenticated');

    const validStore = createAppStore();
    jest.mocked(tokenVault.get).mockResolvedValueOnce('stored-refresh');
    const refresh = jest.fn().mockResolvedValue(credentials);
    await bootstrapSession(validStore.dispatch, refresh);
    expect(refresh).toHaveBeenCalledWith('stored-refresh');
    expect(validStore.getState().auth.status).toBe('authenticated');
  });

  it('clears terminal refresh failures but retains tokens for transient failures', async () => {
    const invalidStore = createAppStore();
    jest.mocked(tokenVault.get).mockResolvedValueOnce('invalid-refresh');
    await bootstrapSession(
      invalidStore.dispatch,
      jest.fn().mockRejectedValue({ kind: 'http', status: 401, message: 'Invalid refresh token' }),
    );
    expect(tokenVault.clear).toHaveBeenCalled();
    expect(invalidStore.getState().auth.status).toBe('unauthenticated');

    jest.clearAllMocks();
    jest.mocked(tokenVault.get).mockResolvedValueOnce('offline-refresh');
    const offlineStore = createAppStore();
    await bootstrapSession(
      offlineStore.dispatch,
      jest.fn().mockRejectedValue({ kind: 'network', status: null, message: 'Offline' }),
    );
    expect(tokenVault.clear).not.toHaveBeenCalled();
    expect(offlineStore.getState().auth).toMatchObject({
      status: 'bootstrapping',
      bootstrapError: 'Offline',
    });
  });

  it('clears memory, secure storage, Redux, and API caches locally', async () => {
    const store = createAppStore();
    await commitSession(credentials, store.dispatch);
    await store.dispatch(workspaceApi.util.upsertQueryData('getTenantWorkspace', undefined, {
      tenantId: 'tenant-1',
      knowledgeBase: { id: 'kb-1', name: 'KB', slug: 'default', defaultLocale: 'en' },
      chatbot: { id: 'bot-1', displayName: 'Bot', defaultLocale: 'en', welcomeMessage: 'Hi' },
    }));
    await store.dispatch(chatApi.util.upsertQueryData('listPlaygroundSessions', undefined, []));
    expect(workspaceApi.endpoints.getTenantWorkspace.select()(store.getState()).status).toBe('fulfilled');
    expect(chatApi.endpoints.listPlaygroundSessions.select()(store.getState()).status).toBe('fulfilled');
    await clearLocalSession(store.dispatch);
    expect(accessTokenStore.get()).toBeNull();
    expect(tokenVault.clear).toHaveBeenCalled();
    expect(store.getState().auth.status).toBe('unauthenticated');
    expect(workspaceApi.endpoints.getTenantWorkspace.select()(store.getState()).status).toBe('uninitialized');
    expect(chatApi.endpoints.listPlaygroundSessions.select()(store.getState()).status).toBe('uninitialized');
  });
});

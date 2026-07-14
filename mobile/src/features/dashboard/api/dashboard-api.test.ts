import { dashboardApi } from '@/features/dashboard/api/dashboard-api';
import type { DashboardSummary } from '@/features/dashboard/types';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { springApi } from '@/services/api/api';
import { createAppStore } from '@/store';

const summary: DashboardSummary = {
  activeUsers: 3,
  activeUsersAddedThisWeek: 1,
  documentsAddedThisWeek: 2,
  recentDocuments: [],
  storageLimitBytes: 1000,
  storedDocumentBytes: 400,
  totalDocuments: 5,
  userMessagesPreviousMonth: 8,
  userMessagesThisMonth: 10,
};

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  });
}

describe('dashboard API', () => {
  beforeAll(() => {
    jest.useFakeTimers();
  });

  beforeEach(() => {
    jest.restoreAllMocks();
    accessTokenStore.set('access-token');
  });

  afterEach(() => {
    accessTokenStore.set(null);
    jest.clearAllTimers();
  });

  afterAll(() => {
    jest.useRealTimers();
  });

  it('loads the authenticated dashboard summary from the Spring contract', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(summary));
    const store = createAppStore();

    const query = store.dispatch(
      dashboardApi.endpoints.getDashboardSummary.initiate(),
    );
    const result = await query.unwrap();

    expect(result).toEqual(summary);
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.url.endsWith('/dashboard/summary')).toBe(true);
    expect(request.headers.get('Authorization')).toBe('Bearer access-token');
    query.unsubscribe();
    store.dispatch(springApi.util.resetApiState());
  });

  it('removes cached tenant dashboard data when the Spring cache resets', async () => {
    jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(summary));
    const store = createAppStore();
    const query = store.dispatch(dashboardApi.endpoints.getDashboardSummary.initiate());
    await query.unwrap();
    expect(dashboardApi.endpoints.getDashboardSummary.select()(store.getState()).data).toEqual(summary);

    query.unsubscribe();
    store.dispatch(springApi.util.resetApiState());

    expect(dashboardApi.endpoints.getDashboardSummary.select()(store.getState()).status).toBe('uninitialized');
  });
});

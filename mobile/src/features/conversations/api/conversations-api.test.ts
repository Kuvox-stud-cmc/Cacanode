import { conversationsApi } from '@/features/conversations/api/conversations-api';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { aiApi } from '@/services/api/api';
import { createAppStore } from '@/store';

function jsonResponse(body: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: status === 204 ? undefined : { 'Content-Type': 'application/json' },
  });
}

const listItem = {
  id: 'conversation-1',
  channel: 'CUSTOM_API',
  external_user_id: 'external-1',
  customer_name: null,
  customer_email: 'customer@example.com',
  status: 'OPEN',
  message_count: 2,
  created_at: '2026-07-14T08:00:00Z',
  updated_at: '2026-07-14T08:05:00Z',
  closed_at: null,
};

const detail = {
  ...listItem,
  customer_metadata: { plan: 'pro' },
  messages: [
    {
      role: 'assistant',
      content: 'Answer [S1].',
      sequence_number: 2,
      citations: [{
        id: 'S1', document_id: 'doc-1', source_name: 'policy.pdf', page_number: 1,
        chunk_index: 0, score: 0.9, snippet: 'Policy excerpt', section_path: ['Returns'],
      }],
      action: { type: 'ticket_draft', title: 'Follow up', description: 'Call customer' },
    },
  ],
};

describe('conversations API', () => {
  beforeAll(() => jest.useFakeTimers());
  beforeEach(() => {
    jest.restoreAllMocks();
    accessTokenStore.set('access-token');
  });

  afterEach(() => {
    accessTokenStore.set(null);
    jest.clearAllTimers();
  });
  afterAll(() => jest.useRealTimers());

  it('sends 50-item paging and combined status/channel filters', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([listItem]));
    const store = createAppStore();
    const query = store.dispatch(conversationsApi.endpoints.listConversations.initiate({
      status: 'OPEN', channel: 'CUSTOM_API', limit: 50, offset: 100,
    }));

    await expect(query.unwrap()).resolves.toEqual([{
      id: 'conversation-1', channel: 'CUSTOM_API', status: 'OPEN', messageCount: 2,
      customer: { externalId: 'external-1', name: null, email: 'customer@example.com' },
      createdAt: '2026-07-14T08:00:00Z', updatedAt: '2026-07-14T08:05:00Z', closedAt: null,
    }]);
    const request = fetchMock.mock.calls[0][0] as Request;
    const url = new URL(request.url);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '50', offset: '100', conversation_status: 'OPEN', channel: 'CUSTOM_API',
    });
    expect(url.searchParams.has('tenantId')).toBe(false);
    query.unsubscribe();
    store.dispatch(aiApi.util.resetApiState());
  });

  it('maps the unchanged detail contract including citations, metadata, and actions', async () => {
    jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(detail));
    const store = createAppStore();
    const query = store.dispatch(conversationsApi.endpoints.getConversation.initiate('conversation-1'));

    await expect(query.unwrap()).resolves.toMatchObject({
      id: 'conversation-1',
      customer: { email: 'customer@example.com', metadata: { plan: 'pro' } },
      messages: [{
        id: 'message-2', role: 'assistant', sequenceNumber: 2,
        citations: [{ documentId: 'doc-1', sourceName: 'policy.pdf', sectionPath: ['Returns'] }],
        action: { type: 'ticket_draft', title: 'Follow up', description: 'Call customer' },
      }],
    });
    query.unsubscribe();
    store.dispatch(aiApi.util.resetApiState());
  });

  it('closes through the tenant-authenticated route and invalidates list/detail caches', async () => {
    const calls: string[] = [];
    jest.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const request = input as Request;
      calls.push(`${request.method} ${new URL(request.url).pathname}`);
      if (request.method === 'DELETE') return jsonResponse(null, 204);
      if (new URL(request.url).pathname.endsWith('/conversation-1')) return jsonResponse(detail);
      return jsonResponse([listItem]);
    });
    const store = createAppStore();
    const list = store.dispatch(conversationsApi.endpoints.listConversations.initiate({ offset: 0 }));
    const loadedDetail = store.dispatch(conversationsApi.endpoints.getConversation.initiate('conversation-1'));
    await Promise.all([list.unwrap(), loadedDetail.unwrap()]);

    const close = store.dispatch(conversationsApi.endpoints.closeConversation.initiate('conversation-1'));
    await close.unwrap();
    await Promise.all(store.dispatch(aiApi.util.getRunningQueriesThunk()));

    expect(calls).toContain('DELETE /api/v1/chat/sessions/conversation-1');
    expect(calls.filter((call) => call === 'GET /api/v1/chat/conversations').length).toBeGreaterThan(1);
    expect(calls.filter((call) => call === 'GET /api/v1/chat/conversations/conversation-1').length).toBeGreaterThan(1);
    list.unsubscribe();
    loadedDetail.unsubscribe();
    close.reset();
    store.dispatch(aiApi.util.resetApiState());
  });
});

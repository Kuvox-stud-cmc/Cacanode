import { ticketsApi } from '@/features/tickets/api/tickets-api';
import { springApi } from '@/services/api/api';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { createAppStore } from '@/store';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const ticket = {
  id: 'ticket-1', chatbotId: 'bot-1', sessionId: 'conversation-1', externalUserId: 'external-1',
  customerName: null, customerEmail: 'customer@example.com', source: 'CUSTOM_API',
  title: 'Refund request', description: 'Customer needs help.', status: 'OPEN', priority: 'HIGH',
  assignedTo: null, assignedToName: null, resolvedAt: null,
  createdAt: '2026-07-14T08:00:00Z', updatedAt: '2026-07-14T08:05:00Z', notes: [],
};

const page = {
  content: [ticket], number: 0, size: 50, totalElements: 1, totalPages: 1, first: true, last: true,
};

describe('tickets API', () => {
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

  it('sends full filters and maps Spring page responses', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(page));
    const store = createAppStore();
    const query = store.dispatch(ticketsApi.endpoints.listTickets.initiate({
      page: 2, size: 50, status: 'IN_PROGRESS', priority: 'URGENT', source: 'CUSTOM_API',
      assignee: '10000000-0000-4000-8000-000000000001',
    }));

    await expect(query.unwrap()).resolves.toEqual(page);
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(Object.fromEntries(new URL(request.url).searchParams)).toEqual({
      page: '2', size: '50', status: 'IN_PROGRESS', priority: 'URGENT', source: 'CUSTOM_API',
      assignedTo: '10000000-0000-4000-8000-000000000001',
    });
    expect(request.headers.get('Authorization')).toBe('Bearer access-token');
    query.unsubscribe();
    store.dispatch(springApi.util.resetApiState());
  });

  it('encodes unassigned separately and supplies empty notes when omitted', async () => {
    const withoutNotes = { ...ticket } as Record<string, unknown>;
    delete withoutNotes.notes;
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      ...page, content: [withoutNotes],
    }));
    const store = createAppStore();
    const query = store.dispatch(ticketsApi.endpoints.listTickets.initiate({
      page: 0, assignee: 'unassigned',
    }));

    await expect(query.unwrap()).resolves.toMatchObject({ content: [{ notes: [] }] });
    expect(Object.fromEntries(new URL((fetchMock.mock.calls[0][0] as Request).url).searchParams))
      .toEqual({ page: '0', size: '50', unassigned: 'true' });
    query.unsubscribe();
    store.dispatch(springApi.util.resetApiState());
  });

  it('uses exact update/note payloads and invalidates active list/detail/dashboard caches', async () => {
    const calls: { method: string; path: string; body?: unknown }[] = [];
    jest.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request;
      const path = new URL(request.url).pathname;
      const body = request.method === 'GET' ? undefined : await request.clone().json();
      calls.push({ method: request.method, path, body });
      if (path.endsWith('/notes')) return jsonResponse({
        id: 'note-1', authorId: 'user-1', authorName: 'Ada', content: 'Follow up', createdAt: '2026-07-14T09:00:00Z',
      });
      if (path.endsWith('/ticket-1') && request.method === 'PATCH') return jsonResponse({ ...ticket, status: 'RESOLVED' });
      if (path.endsWith('/ticket-1')) return jsonResponse(ticket);
      return jsonResponse(page);
    });
    const store = createAppStore();
    const list = store.dispatch(ticketsApi.endpoints.listTickets.initiate({ page: 0 }));
    const detail = store.dispatch(ticketsApi.endpoints.getTicket.initiate('ticket-1'));
    await Promise.all([list.unwrap(), detail.unwrap()]);

    const update = store.dispatch(ticketsApi.endpoints.updateTicket.initiate({
      ticketId: 'ticket-1', update: { status: 'RESOLVED', clearAssignee: true },
    }));
    await update.unwrap();
    await Promise.all(store.dispatch(springApi.util.getRunningQueriesThunk()));
    const note = store.dispatch(ticketsApi.endpoints.addTicketNote.initiate({
      ticketId: 'ticket-1', content: 'Follow up',
    }));
    await note.unwrap();
    await Promise.all(store.dispatch(springApi.util.getRunningQueriesThunk()));

    expect(calls).toContainEqual({
      method: 'PATCH', path: '/api/v1/tenants/me/tickets/ticket-1',
      body: { status: 'RESOLVED', clearAssignee: true },
    });
    expect(calls).toContainEqual({
      method: 'POST', path: '/api/v1/tenants/me/tickets/ticket-1/notes', body: { content: 'Follow up' },
    });
    expect(calls.filter(({ method, path }) => method === 'GET' && path.endsWith('/tickets')).length)
      .toBeGreaterThan(2);
    expect(calls.filter(({ method, path }) => method === 'GET' && path.endsWith('/ticket-1')).length)
      .toBeGreaterThan(2);
    list.unsubscribe();
    detail.unsubscribe();
    update.reset();
    note.reset();
    store.dispatch(springApi.util.resetApiState());
  });
});

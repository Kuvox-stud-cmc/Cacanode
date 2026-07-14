import {
  CHAT_HISTORY_PAGE_SIZE,
  CHAT_MESSAGE_TIMEOUT_MS,
  chatApi,
} from '@/features/chat/api/chat-api';
import { workspaceApi } from '@/features/chat/api/workspace-api';
import type { TenantWorkspace } from '@/features/chat/types';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { aiApi, springApi } from '@/services/api/api';
import { createAppStore } from '@/store';

const workspace: TenantWorkspace = {
  tenantId: 'tenant-1',
  knowledgeBase: {
    id: 'kb-1',
    name: 'Knowledge base',
    slug: 'default',
    defaultLocale: 'vi-VN',
  },
  chatbot: {
    id: 'bot-1',
    displayName: 'Assistant',
    defaultLocale: 'en-US',
    welcomeMessage: 'Hello',
  },
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    headers: status === 204 ? undefined : { 'Content-Type': 'application/json' },
    status,
  });
}

describe('employee chat API', () => {
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

  it('loads the Spring workspace and creates a FastAPI session with snake-case payloads', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request;
      if (request.url.includes('/tenants/me/workspace')) return jsonResponse(workspace);
      expect(await request.json()).toEqual({
        chatbot_id: 'bot-1',
        knowledge_base_id: 'kb-1',
        locale: 'en-US',
      });
      return jsonResponse({
        id: 'session-1',
        chatbot_id: 'bot-1',
        knowledge_base_id: 'kb-1',
        tenant_id: 'tenant-1',
        locale: 'en-US',
      });
    });
    const store = createAppStore();

    const workspaceSubscription = store.dispatch(workspaceApi.endpoints.getTenantWorkspace.initiate());
    await expect(workspaceSubscription.unwrap()).resolves.toEqual(workspace);
    const createSubscription = store.dispatch(chatApi.endpoints.createChatSession.initiate(workspace));
    await expect(createSubscription.unwrap()).resolves.toEqual({
      id: 'session-1',
      chatbotId: 'bot-1',
      knowledgeBaseId: 'kb-1',
      tenantId: 'tenant-1',
      locale: 'en-US',
    });

    const requests = fetchMock.mock.calls.map(([input]) => input as Request);
    expect(requests.every((request) => request.headers.get('Authorization') === 'Bearer access-token')).toBe(true);
    expect(requests[0].url.endsWith('/tenants/me/workspace')).toBe(true);
    expect(requests[1].url.endsWith('/chat/sessions')).toBe(true);
    workspaceSubscription.unsubscribe();
    store.dispatch(springApi.util.resetApiState());
    store.dispatch(aiApi.util.resetApiState());
  });

  it('maps session history, assistant citations, and employee history from snake case', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request;
      if (request.method === 'POST') {
        expect(await request.json()).toEqual({ content: 'Where is the policy?' });
        return jsonResponse({
          role: 'assistant',
          content: 'On page 3.',
          citations: [citationResponse()],
          action: { kind: 'answer' },
        });
      }
      if (request.url.includes('/playground/sessions')) {
        return jsonResponse([{
          id: 'session-1',
          title: 'First question',
          message_count: 4,
          status: 'OPEN',
          created_at: '2026-07-14T01:00:00Z',
          last_activity_at: '2026-07-14T02:00:00Z',
        }]);
      }
      return jsonResponse([{
        role: 'assistant',
        content: 'On page 3.',
        citations: [citationResponse()],
        sequence_number: 2,
        action: { kind: 'answer' },
      }]);
    });
    const store = createAppStore();

    const send = store.dispatch(chatApi.endpoints.submitChatMessage.initiate({
      sessionId: 'session-1',
      content: 'Where is the policy?',
    }));
    await expect(send.unwrap()).resolves.toMatchObject({
      content: 'On page 3.',
      citations: [{
        documentId: 'doc-1',
        sourceName: 'policy.xlsx',
        pageNumber: 3,
        sectionPath: ['Benefits', 'Leave'],
        sheetName: 'Policy',
        cellRange: 'B2:D8',
        blockType: 'table',
        modality: 'spreadsheet',
      }],
    });

    const history = store.dispatch(chatApi.endpoints.getChatHistory.initiate('session-1'));
    await expect(history.unwrap()).resolves.toMatchObject([{
      role: 'assistant',
      sequenceNumber: 2,
      citations: [{ documentId: 'doc-1' }],
    }]);

    const sessions = store.dispatch(chatApi.endpoints.listPlaygroundSessions.initiate());
    await expect(sessions.unwrap()).resolves.toEqual([{
      id: 'session-1',
      title: 'First question',
      messageCount: 4,
      status: 'OPEN',
      createdAt: '2026-07-14T01:00:00Z',
      lastActivityAt: '2026-07-14T02:00:00Z',
    }]);

    const listRequest = fetchMock.mock.calls
      .map(([input]) => input as Request)
      .find((request) => request.url.includes('/playground/sessions'));
    expect(listRequest?.url).toContain('limit=50');
    expect(listRequest?.url).toContain('offset=0');
    send.reset();
    history.unsubscribe();
    sessions.unsubscribe();
    store.dispatch(aiApi.util.resetApiState());
  });

  it('retrieves complete transcripts in 200-message pages without sending identity fields', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request;
      const url = new URL(request.url);
      const after = Number(url.searchParams.get('after'));
      expect(url.searchParams.get('limit')).toBe(String(CHAT_HISTORY_PAGE_SIZE));
      expect(url.searchParams.has('tenant_id')).toBe(false);
      expect(url.searchParams.has('user_id')).toBe(false);
      if (after === 0) {
        return jsonResponse(Array.from({ length: CHAT_HISTORY_PAGE_SIZE }, (_, index) => ({
          role: index % 2 ? 'assistant' : 'user',
          content: `Message ${index + 1}`,
          citations: [],
          sequence_number: index + 1,
        })));
      }
      return jsonResponse([{
        role: 'assistant',
        content: 'Final message',
        citations: [],
        sequence_number: 201,
      }]);
    });
    const store = createAppStore();
    const query = store.dispatch(chatApi.endpoints.getChatHistory.initiate('session-1'));

    const result = await query.unwrap();
    expect(result).toHaveLength(201);
    expect(result.at(-1)?.sequenceNumber).toBe(201);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    query.unsubscribe();
    store.dispatch(aiApi.util.resetApiState());
  });

  it('stops pagination when a full page does not advance its sequence number', async () => {
    jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(
      Array.from({ length: CHAT_HISTORY_PAGE_SIZE }, (_, index) => ({
        role: 'user',
        content: `Message ${index}`,
        citations: [],
        sequence_number: 0,
      })),
    ));
    const store = createAppStore();
    const query = store.dispatch(chatApi.endpoints.getChatHistory.initiate('session-1'));
    await expect(query.unwrap()).resolves.toHaveLength(CHAT_HISTORY_PAGE_SIZE);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    query.unsubscribe();
    store.dispatch(aiApi.util.resetApiState());
  });

  it('uses a 90-second timeout and does not retry failed message mutations', async () => {
    let requestSignal: AbortSignal | undefined;
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request;
      requestSignal = request.signal;
      return await new Promise<Response>((_resolve, reject) => {
        request.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      });
    });
    const store = createAppStore();
    const send = store.dispatch(chatApi.endpoints.submitChatMessage.initiate({
      sessionId: 'session-1',
      content: 'A slow question',
    }));

    await jest.advanceTimersByTimeAsync(CHAT_MESSAGE_TIMEOUT_MS - 1);
    expect(requestSignal?.aborted).toBe(false);
    await jest.advanceTimersByTimeAsync(1);
    expect(requestSignal?.aborted).toBe(true);
    await expect(send.unwrap()).rejects.toMatchObject({ kind: 'network' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    send.reset();
    store.dispatch(aiApi.util.resetApiState());
  });

  it('hides a session with no client-supplied tenant or employee identity', async () => {
    const fetchMock = jest.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(null, 204));
    const store = createAppStore();
    const hide = store.dispatch(chatApi.endpoints.hidePlaygroundSession.initiate('session-1'));
    await expect(hide.unwrap()).resolves.toBeUndefined();
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.method).toBe('DELETE');
    expect(request.url.endsWith('/chat/playground/sessions/session-1')).toBe(true);
    expect(request.url).not.toContain('tenant');
    expect(request.url).not.toContain('user');
    hide.reset();
    store.dispatch(aiApi.util.resetApiState());
  });
});

function citationResponse() {
  return {
    id: 'S1',
    document_id: 'doc-1',
    source_name: 'policy.xlsx',
    page_number: 3,
    chunk_index: 2,
    score: 0.92,
    snippet: 'Employees receive annual leave.',
    unit_id: 'unit-1',
    modality: 'spreadsheet',
    section_path: ['Benefits', 'Leave'],
    block_type: 'table',
    sheet_name: 'Policy',
    cell_range: 'B2:D8',
    table_id: 'table-1',
  };
}

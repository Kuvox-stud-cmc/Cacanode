import {
  canSendMessage,
  chatFailureMessage,
  chatReducer,
  initialChatState,
  NO_INFORMATION_RESPONSE,
} from '@/features/chat/model/chat-state';
import type { AssistantResponse, ChatHistoryMessage, PlaygroundSession } from '@/features/chat/types';

const sessions: PlaygroundSession[] = [
  {
    id: 'newest',
    title: 'Newest',
    messageCount: 2,
    status: 'OPEN',
    createdAt: '2026-07-14T02:00:00Z',
    lastActivityAt: '2026-07-14T03:00:00Z',
  },
  {
    id: 'older',
    title: 'Older',
    messageCount: 4,
    status: 'OPEN',
    createdAt: '2026-07-13T02:00:00Z',
    lastActivityAt: '2026-07-13T03:00:00Z',
  },
];

describe('chat transcript reducer', () => {
  it('selects only the newest session initially and New Chat clears draft and transcript', () => {
    const loaded = chatReducer(initialChatState, { type: 'sessionsLoaded', sessions });
    expect(loaded.selectedSessionId).toBe('newest');
    const changed = chatReducer({
      ...loaded,
      draft: 'draft',
      messages: [message('existing', 'user')],
    }, { type: 'newChat' });
    expect(changed).toMatchObject({ selectedSessionId: null, draft: '', messages: [] });

    const ignoredReload = chatReducer(changed, { type: 'sessionsLoaded', sessions });
    expect(ignoredReload.selectedSessionId).toBeNull();
  });

  it('filters system messages and ignores stale history responses after a session switch', () => {
    let state = chatReducer(initialChatState, { type: 'sessionsLoaded', sessions });
    state = chatReducer(state, { type: 'historyStarted', sessionId: 'newest', requestId: 'request-1' });
    state = chatReducer(state, { type: 'sessionSelected', sessionId: 'older' });
    const stale = chatReducer(state, {
      type: 'historySucceeded',
      sessionId: 'newest',
      requestId: 'request-1',
      messages: [historyMessage('user', 'Stale')],
    });
    expect(stale.messages).toEqual([]);

    state = chatReducer(stale, { type: 'historyStarted', sessionId: 'older', requestId: 'request-2' });
    state = chatReducer(state, {
      type: 'historySucceeded',
      sessionId: 'older',
      requestId: 'request-2',
      messages: [
        historyMessage('system', 'Hidden instruction'),
        historyMessage('user', 'Visible question'),
        historyMessage('assistant', 'Visible answer'),
      ],
    });
    expect(state.messages.map((item) => item.content)).toEqual(['Visible question', 'Visible answer']);
  });

  it('replaces exactly one pending assistant on success and failure without duplicating the user', () => {
    let state = chatReducer(initialChatState, {
      type: 'sendStarted',
      sendId: 'assistant-1',
      userId: 'user-1',
      content: 'Question',
    });
    expect(state.messages).toHaveLength(2);
    state = chatReducer(state, {
      type: 'sendSucceeded',
      sendId: 'assistant-1',
      response: assistantResponse('Answer'),
    });
    expect(state.messages).toHaveLength(2);
    expect(state.messages.map((item) => item.content)).toEqual(['Question', 'Answer']);

    state = chatReducer(state, {
      type: 'sendStarted',
      sendId: 'assistant-2',
      userId: 'user-2',
      content: 'Second question',
    });
    state = chatReducer(state, {
      type: 'sendFailed',
      sendId: 'assistant-2',
      message: 'Reload to check.',
    });
    expect(state.messages).toHaveLength(4);
    expect(state.messages.filter((item) => item.role === 'user')).toHaveLength(2);
    expect(state.messages.at(-1)).toMatchObject({ status: 'failed', failureMessage: 'Reload to check.' });
  });

  it('clears inaccessible sessions without rendering their content', () => {
    let state = chatReducer(initialChatState, { type: 'sessionsLoaded', sessions });
    state = chatReducer(state, { type: 'historyStarted', sessionId: 'newest', requestId: 'request-1' });
    state = chatReducer(state, {
      type: 'historyFailed',
      sessionId: 'newest',
      requestId: 'request-1',
      inaccessible: true,
      message: 'Not found',
    });
    expect(state.selectedSessionId).toBeNull();
    expect(state.messages).toEqual([]);
    expect(state.unavailableSessionIds).toEqual(['newest']);
  });

  it('marks the existing no-information answer while preserving its exact content', () => {
    let state = chatReducer(initialChatState, {
      type: 'sendStarted', sendId: 'assistant-1', userId: 'user-1', content: 'Question',
    });
    state = chatReducer(state, {
      type: 'sendSucceeded',
      sendId: 'assistant-1',
      response: assistantResponse(NO_INFORMATION_RESPONSE),
    });
    expect(state.messages[1]).toMatchObject({ content: NO_INFORMATION_RESPONSE, noInformation: true });
  });
});

describe('chat validation and safe errors', () => {
  it('rejects blank, loading, sending, and over-limit composer input', () => {
    expect(canSendMessage('   ', true, false)).toBe(false);
    expect(canSendMessage('question', false, false)).toBe(false);
    expect(canSendMessage('question', true, true)).toBe(false);
    expect(canSendMessage('x'.repeat(32_001), true, false)).toBe(false);
    expect(canSendMessage('question', true, false)).toBe(true);
  });

  it.each([
    ['MESSAGE_QUOTA_EXCEEDED', 'message quota'],
    ['MODEL_TIMEOUT', 'took too long'],
    ['MODEL_PROVIDER_ERROR', 'temporarily unavailable'],
    ['CHAT_SESSION_STORE_UNAVAILABLE', 'storage is temporarily unavailable'],
    ['WORKSPACE_NOT_FOUND', 'workspace is unavailable'],
  ])('maps %s to an actionable safe message', (code, expected) => {
    expect(chatFailureMessage({
      code,
      kind: 'http',
      message: 'unsafe backend details',
      messages: [],
      requestId: null,
      status: 500,
    })).toContain(expected);
  });
});

function message(content: string, role: 'user' | 'assistant') {
  return { id: content, role, content, citations: [], status: 'sent' as const };
}

function historyMessage(role: ChatHistoryMessage['role'], content: string): ChatHistoryMessage {
  return { role, content, citations: [], sequenceNumber: 1, action: null };
}

function assistantResponse(content: string): AssistantResponse {
  return { role: 'assistant', content, citations: [], action: null };
}

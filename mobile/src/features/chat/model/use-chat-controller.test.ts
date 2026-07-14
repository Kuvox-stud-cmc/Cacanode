import { act, renderHook, waitFor } from '@testing-library/react-native';

import { useChatController } from '@/features/chat/model/use-chat-controller';
import type { PlaygroundSession, TenantWorkspace } from '@/features/chat/types';

const mockLoadHistory = jest.fn();
const mockCreateSession = jest.fn();
const mockSubmitMessage = jest.fn();
const mockHideSession = jest.fn();
const mockRefetchSessions = jest.fn();
const mockRefetchWorkspace = jest.fn();
const mockUseWorkspace = jest.fn();
const mockUseSessions = jest.fn();

jest.mock('@/features/chat/api/workspace-api', () => ({
  useGetTenantWorkspaceQuery: () => mockUseWorkspace(),
}));

jest.mock('@/features/chat/api/chat-api', () => ({
  useLazyGetChatHistoryQuery: () => [mockLoadHistory],
  useCreateChatSessionMutation: () => [mockCreateSession],
  useSubmitChatMessageMutation: () => [mockSubmitMessage],
  useHidePlaygroundSessionMutation: () => [mockHideSession, { isLoading: false }],
  useListPlaygroundSessionsQuery: () => mockUseSessions(),
}));

const workspace: TenantWorkspace = {
  tenantId: 'tenant-1',
  knowledgeBase: { id: 'kb-1', name: 'KB', slug: 'default', defaultLocale: 'vi-VN' },
  chatbot: { id: 'bot-1', displayName: 'Assistant', defaultLocale: 'vi-VN', welcomeMessage: 'Hi' },
};

const sessions: PlaygroundSession[] = [
  {
    id: 'session-1', title: 'Latest', messageCount: 2, status: 'OPEN',
    createdAt: '2026-07-14T01:00:00Z', lastActivityAt: '2026-07-14T03:00:00Z',
  },
  {
    id: 'session-2', title: 'Older', messageCount: 2, status: 'OPEN',
    createdAt: '2026-07-13T01:00:00Z', lastActivityAt: '2026-07-13T03:00:00Z',
  },
];

describe('useChatController', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseWorkspace.mockReturnValue({
      data: workspace,
      error: undefined,
      isLoading: false,
      refetch: mockRefetchWorkspace,
    });
    mockUseSessions.mockReturnValue({
      data: sessions,
      error: undefined,
      isFetching: false,
      isLoading: false,
      refetch: mockRefetchSessions,
    });
    mockLoadHistory.mockImplementation((sessionId: string) => ({
      unwrap: jest.fn().mockResolvedValue([
        { role: 'user', content: `Question ${sessionId}`, citations: [], sequenceNumber: 1, action: null },
        { role: 'assistant', content: `Answer ${sessionId}`, citations: [], sequenceNumber: 2, action: null },
      ]),
    }));
    mockCreateSession.mockReturnValue({
      unwrap: jest.fn().mockResolvedValue({ id: 'created-session' }),
    });
    mockSubmitMessage.mockReturnValue({
      unwrap: jest.fn().mockResolvedValue({ role: 'assistant', content: 'Server answer', citations: [], action: null }),
    });
    mockHideSession.mockReturnValue({ unwrap: jest.fn().mockResolvedValue(undefined) });
  });

  it('auto-opens the latest employee session and switches transcripts', async () => {
    const controller = await renderHook(() => useChatController());
    await waitFor(() => expect(controller.result.current.state.selectedSessionId).toBe('session-1'));
    await waitFor(() => expect(controller.result.current.state.messages[1]?.content).toBe('Answer session-1'));

    await act(() => controller.result.current.selectSession('session-2'));
    await waitFor(() => expect(controller.result.current.state.messages[1]?.content).toBe('Answer session-2'));
    expect(mockLoadHistory).toHaveBeenCalledWith('session-1', false);
    expect(mockLoadHistory).toHaveBeenCalledWith('session-2', false);
  });

  it('creates a session lazily on the first New Chat message', async () => {
    const controller = await renderHook(() => useChatController());
    await waitFor(() => expect(controller.result.current.state.selectedSessionId).toBe('session-1'));
    await act(() => controller.result.current.startNewChat());
    await act(() => controller.result.current.setDraft('  First question  '));
    await waitFor(() => expect(controller.result.current.canSend).toBe(true));
    await act(async () => controller.result.current.send());

    expect(mockCreateSession).toHaveBeenCalledWith(workspace);
    expect(mockSubmitMessage).toHaveBeenCalledWith({
      sessionId: 'created-session',
      content: 'First question',
    });
    expect(controller.result.current.state.selectedSessionId).toBe('created-session');
    expect(controller.result.current.state.messages.map((message) => message.content)).toEqual([
      'First question',
      'Server answer',
    ]);
  });

  it('recovers SESSION_NOT_FOUND once with a replacement session and no duplicate messages', async () => {
    mockSubmitMessage
      .mockReturnValueOnce({
        unwrap: jest.fn().mockRejectedValue({ kind: 'http', status: 404, code: 'SESSION_NOT_FOUND' }),
      })
      .mockReturnValueOnce({
        unwrap: jest.fn().mockResolvedValue({ role: 'assistant', content: 'Recovered answer', citations: [], action: null }),
      });
    mockCreateSession.mockReturnValue({
      unwrap: jest.fn().mockResolvedValue({ id: 'replacement-session' }),
    });
    const controller = await renderHook(() => useChatController());
    await waitFor(() => expect(controller.result.current.state.selectedSessionId).toBe('session-1'));
    await act(() => controller.result.current.setDraft('Question'));
    await act(async () => controller.result.current.send());

    expect(mockSubmitMessage).toHaveBeenCalledTimes(2);
    expect(mockCreateSession).toHaveBeenCalledTimes(1);
    expect(controller.result.current.state.selectedSessionId).toBe('replacement-session');
    expect(controller.result.current.state.messages).toHaveLength(4);
    expect(controller.result.current.state.messages.filter((message) => message.content === 'Question')).toHaveLength(1);
    expect(controller.result.current.state.messages.at(-1)?.content).toBe('Recovered answer');
  });

  it('does not resend other failures and replaces the pending assistant with reload guidance', async () => {
    mockSubmitMessage.mockReturnValue({
      unwrap: jest.fn().mockRejectedValue({ kind: 'http', status: 429, code: 'MESSAGE_QUOTA_EXCEEDED' }),
    });
    const controller = await renderHook(() => useChatController());
    await waitFor(() => expect(controller.result.current.state.selectedSessionId).toBe('session-1'));
    await act(() => controller.result.current.setDraft('Question'));
    await act(async () => controller.result.current.send());

    expect(mockSubmitMessage).toHaveBeenCalledTimes(1);
    expect(controller.result.current.state.messages).toHaveLength(4);
    expect(controller.result.current.state.messages.at(-1)).toMatchObject({
      status: 'failed',
      failureMessage: expect.stringContaining('message quota'),
    });
  });

  it.each([
    [{ kind: 'timeout', status: null, code: null }, 'took too long'],
    [{ kind: 'http', status: 502, code: 'MODEL_PROVIDER_ERROR' }, 'temporarily unavailable'],
    [{ kind: 'network', status: null, code: null }, 'connection was interrupted'],
    [{ kind: 'http', status: 503, code: 'CHAT_SESSION_STORE_UNAVAILABLE' }, 'storage is temporarily unavailable'],
  ])('keeps one optimistic pair for %j failures', async (failure, expected) => {
    mockSubmitMessage.mockReturnValue({ unwrap: jest.fn().mockRejectedValue(failure) });
    const controller = await renderHook(() => useChatController());
    await waitFor(() => expect(controller.result.current.state.messages).toHaveLength(2));
    await act(() => controller.result.current.setDraft('One question'));
    await act(async () => controller.result.current.send());

    expect(mockSubmitMessage).toHaveBeenCalledTimes(1);
    expect(controller.result.current.state.messages).toHaveLength(4);
    expect(controller.result.current.state.messages.filter((message) => message.content === 'One question')).toHaveLength(1);
    expect(controller.result.current.state.messages.at(-1)?.failureMessage?.toLowerCase()).toContain(expected);
  });

  it('hiding the active session selects the next available conversation', async () => {
    const controller = await renderHook(() => useChatController());
    await waitFor(() => expect(controller.result.current.state.selectedSessionId).toBe('session-1'));
    await act(async () => {
      await controller.result.current.hideSession(sessions[0]);
    });
    expect(mockHideSession).toHaveBeenCalledWith('session-1');
    expect(controller.result.current.state.selectedSessionId).toBe('session-2');
  });

  it('clears a 404 history response without exposing transcript content', async () => {
    mockLoadHistory.mockReturnValue({
      unwrap: jest.fn().mockRejectedValue({ kind: 'http', status: 404, code: 'SESSION_NOT_FOUND' }),
    });
    const controller = await renderHook(() => useChatController());
    await waitFor(() => expect(controller.result.current.state.unavailableSessionIds).toEqual(['session-1']));
    expect(controller.result.current.state.selectedSessionId).toBeNull();
    expect(controller.result.current.state.messages).toEqual([]);
    expect(mockRefetchSessions).toHaveBeenCalled();
  });
});

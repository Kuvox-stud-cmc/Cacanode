import { fireEvent, render } from '@testing-library/react-native';
import { ChatScreen } from '@/features/chat/screens/chat-screen';
import { CHAT_MAX_LENGTH, NO_INFORMATION_RESPONSE } from '@/features/chat/model/chat-state';
import type { ChatCitation, TranscriptMessage } from '@/features/chat/types';

const mockController = jest.fn();
const setDraft = jest.fn();
const startNewChat = jest.fn();
const selectSession = jest.fn();
const reloadTranscript = jest.fn();
const send = jest.fn();
const hideSession = jest.fn().mockResolvedValue(true);

jest.mock('@/features/chat/model/use-chat-controller', () => ({
  useChatController: () => mockController(),
}));

jest.mock('react-native-safe-area-context', () => {
  const { View } = jest.requireActual<typeof import('react-native')>('react-native');
  return {
    SafeAreaView: ({ children, ...props }: { children: React.ReactNode }) => <View {...props}>{children}</View>,
    useSafeAreaInsets: () => ({ bottom: 0, left: 0, right: 0, top: 0 }),
  };
});

const citation: ChatCitation = {
  id: 'S1',
  documentId: 'doc-1',
  sourceName: 'Employee policy.xlsx',
  pageNumber: 7,
  chunkIndex: 3,
  score: 0.91,
  snippet: 'Annual leave is listed in cells B2:D8.',
  unitId: 'unit-1',
  modality: 'spreadsheet',
  sectionPath: ['Benefits', 'Leave'],
  blockType: 'table',
  sheetName: 'Policy',
  cellRange: 'B2:D8',
  tableId: 'table-1',
};

function controllerValue(overrides: Record<string, unknown> = {}) {
  return {
    state: {
      selectedSessionId: null,
      selectionInitialized: true,
      unavailableSessionIds: [],
      draft: '',
      messages: [] as TranscriptMessage[],
      historyRequestId: null,
      historyStatus: 'idle',
      historyError: null,
      activeSendId: null,
    },
    sessions: [
      {
        id: 'session-1', title: 'Latest question', messageCount: 2, status: 'OPEN',
        createdAt: '2026-07-14T01:00:00Z', lastActivityAt: '2026-07-14T02:00:00Z',
      },
      {
        id: 'session-2', title: 'Older question', messageCount: 4, status: 'OPEN',
        createdAt: '2026-07-13T01:00:00Z', lastActivityAt: '2026-07-13T02:00:00Z',
      },
    ],
    workspace: {
      tenantId: 'tenant-1',
      knowledgeBase: { id: 'kb-1', name: 'KB', slug: 'default', defaultLocale: 'en' },
      chatbot: { id: 'bot-1', displayName: 'Assistant', defaultLocale: 'en', welcomeMessage: 'How can I help?' },
    },
    workspaceLoading: false,
    workspaceError: null,
    retryWorkspace: jest.fn(),
    sessionsLoading: false,
    sessionsError: null,
    retrySessions: jest.fn(),
    sending: false,
    canSend: false,
    hideError: null,
    hiding: false,
    setDraft,
    startNewChat,
    selectSession,
    reloadTranscript,
    send,
    hideSession,
    ...overrides,
  };
}

describe('ChatScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    hideSession.mockResolvedValue(true);
    mockController.mockReturnValue(controllerValue());
  });

  it('uses a keyboard-safe transcript and validates blank composer input', async () => {
    const screen = await render(<ChatScreen />);
    expect(screen.getByTestId('chat-keyboard-layout')).toBeTruthy();
    expect(screen.getByTestId('chat-transcript')).toBeTruthy();
    const input = screen.getByLabelText('Message');
    expect(input.props.maxLength).toBe(CHAT_MAX_LENGTH);
    expect(screen.getByRole('button', { name: 'Send message' }).props.accessibilityState.disabled).toBe(true);
    await fireEvent.changeText(input, '   ');
    expect(setDraft).toHaveBeenCalledWith('   ');
  });

  it('shows the composer count and sends an eligible message', async () => {
    mockController.mockReturnValue(controllerValue({
      canSend: true,
      state: { ...controllerValue().state, draft: 'ready' },
    }));
    const screen = await render(<ChatScreen />);
    expect(screen.getByText('5 / 32,000')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Send message' }));
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('marks the composer busy while a message is sending', async () => {
    mockController.mockReturnValue(controllerValue({
      sending: true,
      canSend: false,
      state: { ...controllerValue().state, activeSendId: 'assistant-1' },
    }));
    const screen = await render(<ChatScreen />);
    expect(screen.getByRole('button', { name: 'Send message' }).props.accessibilityState).toMatchObject({
      busy: true,
      disabled: true,
    });
  });

  it('renders long flexible messages and offers transcript reload after a failed send', async () => {
    const longMessage = 'A long employee question '.repeat(80);
    mockController.mockReturnValue(controllerValue({
      state: {
        ...controllerValue().state,
        selectedSessionId: 'session-1',
        messages: [
          transcript('user-1', 'user', longMessage),
          {
            ...transcript('assistant-1', 'assistant', 'I couldn’t confirm an answer for that message.'),
            status: 'failed',
            failureMessage: 'The connection was interrupted.',
          },
        ],
      },
    }));
    const screen = await render(<ChatScreen />);
    expect(screen.getByText(longMessage).props.numberOfLines).toBeUndefined();
    await fireEvent.press(screen.getByRole('button', { name: 'Reload conversation' }));
    expect(reloadTranscript).toHaveBeenCalledTimes(1);
  });

  it('preserves no-information answers and opens every available citation metadata field', async () => {
    mockController.mockReturnValue(controllerValue({
      state: {
        ...controllerValue().state,
        selectedSessionId: 'session-1',
        messages: [{
          ...transcript('assistant-1', 'assistant', NO_INFORMATION_RESPONSE),
          citations: [citation],
          noInformation: true,
        }],
      },
    }));
    const screen = await render(<ChatScreen />);
    expect(screen.getByText(NO_INFORMATION_RESPONSE)).toBeTruthy();
    expect(screen.getByText(/No matching information was found/)).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Open citation 1: Employee policy.xlsx' }));
    for (const value of [
      'Employee policy.xlsx',
      citation.snippet,
      '7',
      'Benefits › Leave',
      'Policy',
      'B2:D8',
      'table',
      'spreadsheet',
      'unit-1',
      'table-1',
    ]) {
      expect(screen.getByText(value)).toBeTruthy();
    }
    await fireEvent.press(screen.getAllByRole('button', { name: 'Close citation details' })[0]);
    expect(screen.queryByText(citation.snippet)).toBeNull();
  });

  it('supports history selection, New Chat, and confirmed hiding with analytics retention copy', async () => {
    const screen = await render(<ChatScreen />);
    await fireEvent.press(screen.getByRole('button', { name: 'Start new chat' }));
    expect(startNewChat).toHaveBeenCalledTimes(1);

    await fireEvent.press(screen.getByRole('button', { name: 'Open conversation history' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Older question' }));
    expect(selectSession).toHaveBeenCalledWith('session-2');

    await fireEvent.press(screen.getByRole('button', { name: 'Open conversation history' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Hide Latest question' }));
    expect(screen.getByText(/retained for workspace analytics/)).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Hide conversation' }));
    expect(hideSession).toHaveBeenCalledWith(expect.objectContaining({ id: 'session-1' }));
  });

  it('renders workspace loading/error and history loading/empty/retry states', async () => {
    mockController.mockReturnValue(controllerValue({ workspace: undefined, workspaceLoading: true }));
    const screen = await render(<ChatScreen />);
    expect(screen.getByText('Loading employee chat')).toBeTruthy();

    const retryWorkspace = jest.fn();
    mockController.mockReturnValue(controllerValue({
      workspace: undefined,
      workspaceLoading: false,
      workspaceError: 'Workspace unavailable.',
      retryWorkspace,
    }));
    await screen.rerender(<ChatScreen />);
    expect(screen.getByText('Unable to open employee chat')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Try again' }));
    expect(retryWorkspace).toHaveBeenCalledTimes(1);

    mockController.mockReturnValue(controllerValue({ sessions: [], sessionsLoading: true }));
    await screen.rerender(<ChatScreen />);
    await fireEvent.press(screen.getByRole('button', { name: 'Open conversation history' }));
    expect(screen.getByText('Loading history')).toBeTruthy();
    await fireEvent.press(screen.getAllByRole('button', { name: 'Close conversation history' })[0]);

    mockController.mockReturnValue(controllerValue({ sessions: [], sessionsLoading: false }));
    await screen.rerender(<ChatScreen />);
    await fireEvent.press(screen.getByRole('button', { name: 'Open conversation history' }));
    expect(screen.getByText('No conversation history')).toBeTruthy();
    await fireEvent.press(screen.getAllByRole('button', { name: 'Close conversation history' })[0]);

    const retrySessions = jest.fn();
    mockController.mockReturnValue(controllerValue({
      sessions: [],
      sessionsError: 'Unable to load history.',
      retrySessions,
    }));
    await screen.rerender(<ChatScreen />);
    await fireEvent.press(screen.getByRole('button', { name: 'Open conversation history' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Try again' }));
    expect(retrySessions).toHaveBeenCalledTimes(1);
  });
});

function transcript(
  id: string,
  role: TranscriptMessage['role'],
  content: string,
): TranscriptMessage {
  return { id, role, content, citations: [], status: 'sent' };
}

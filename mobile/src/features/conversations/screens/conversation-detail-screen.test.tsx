import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ConversationDetailScreen } from '@/features/conversations/screens/conversation-detail-screen';
import type { ConversationDetail } from '@/features/conversations/types';

const mockReplace = jest.fn();
const mockRefetch = jest.fn();
const mockClose = jest.fn();
const mockUseConversation = jest.fn();
let mockRouteParams: Record<string, string> = {
  conversationId: 'conversation-1', status: 'CLOSED', channel: 'CUSTOM_API',
};

jest.mock('expo-router', () => ({
  router: { replace: (...args: unknown[]) => mockReplace(...args) },
  useLocalSearchParams: () => mockRouteParams,
}));

jest.mock('@/features/conversations/api/conversations-api', () => ({
  useGetConversationQuery: () => mockUseConversation(),
  useCloseConversationMutation: () => [mockClose, { isLoading: false }],
}));

const conversation: ConversationDetail = {
  id: 'conversation-1',
  channel: 'CUSTOM_API',
  customer: {
    name: null,
    email: 'customer@example.com',
    externalId: 'external-1',
    metadata: { plan: 'pro', nested: { hidden: true }, long: 'x'.repeat(250) },
  },
  status: 'OPEN',
  createdAt: '2026-07-14T08:00:00Z',
  updatedAt: '2026-07-14T08:05:00Z',
  closedAt: null,
  messages: [
    {
      id: 'system-1', role: 'system', content: 'Conversation transferred.', citations: [],
      sequenceNumber: 1, action: null,
    },
    {
      id: 'assistant-2', role: 'assistant', content: 'A draft is ready [S1].', sequenceNumber: 2,
      citations: [{
        id: 'S1', documentId: 'doc-1', sourceName: 'policy.pdf', pageNumber: 2,
        chunkIndex: 0, score: 0.9, snippet: 'Relevant policy excerpt', unitId: null,
        modality: null, sectionPath: [], blockType: null, sheetName: null, cellRange: null,
        tableId: null,
      }],
      action: { type: 'ticket_draft', title: 'Follow up', description: 'Call the customer.' },
    },
    {
      id: 'assistant-3', role: 'assistant', content: 'Unknown action.', citations: [],
      sequenceNumber: 3, action: { type: 'ticket_created', id: 'ticket-1' },
    },
  ],
};

async function renderScreen() {
  return render(
    <SafeAreaProvider initialMetrics={{
      frame: { x: 0, y: 0, width: 390, height: 844 },
      insets: { top: 0, right: 0, bottom: 0, left: 0 },
    }}>
      <ConversationDetailScreen />
    </SafeAreaProvider>,
  );
}

describe('ConversationDetailScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRouteParams = { conversationId: 'conversation-1', status: 'CLOSED', channel: 'CUSTOM_API' };
    mockRefetch.mockResolvedValue({ data: { ...conversation, status: 'CLOSED' } });
    mockClose.mockReturnValue({ unwrap: () => Promise.resolve() });
    mockUseConversation.mockReturnValue({
      data: conversation, error: undefined, isError: false, isFetching: false,
      isLoading: false, refetch: mockRefetch,
    });
  });

  it('shows identity fallbacks, explicit unavailable fields, bounded metadata, system messages, and ticket drafts', async () => {
    const screen = await renderScreen();
    expect(screen.getAllByText('customer@example.com')).toHaveLength(2);
    expect(screen.getByText('Conversation transferred.')).toBeTruthy();
    expect(screen.getAllByText('Unavailable')).toHaveLength(1);
    expect(screen.getByText('pro')).toBeTruthy();
    expect(screen.queryByText('nested')).toBeNull();
    expect(screen.getByText('Follow up')).toBeTruthy();
    expect(screen.getByText('Draft only—no ticket has been created.')).toBeTruthy();
    expect(screen.getAllByText('Ticket draft')).toHaveLength(1);
  });

  it('opens reusable citation details and closes only after confirmation succeeds', async () => {
    const screen = await renderScreen();
    await fireEvent.press(screen.getByRole('button', { name: 'Citation 1: policy.pdf' }));
    expect(screen.getByText('Relevant policy excerpt')).toBeTruthy();

    await fireEvent.press(screen.getByRole('button', { name: 'Close conversation' }));
    expect(screen.getByText('Close conversation?')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(mockClose).toHaveBeenCalledWith('conversation-1'));
    await waitFor(() => expect(mockRefetch).toHaveBeenCalled());
    expect(screen.getByText('Conversation closed.')).toBeTruthy();
  });

  it('disables closing for an already closed conversation', async () => {
    mockUseConversation.mockReturnValue({
      data: { ...conversation, status: 'CLOSED', closedAt: '2026-07-14T09:00:00Z' },
      error: undefined, isError: false, isFetching: false, isLoading: false, refetch: mockRefetch,
    });
    const screen = await renderScreen();
    expect(screen.getByRole('button', { name: 'Conversation closed' }).props.accessibilityState)
      .toMatchObject({ disabled: true });
  });

  it('keeps large transcripts virtualized and supports pull refresh', async () => {
    const messages = Array.from({ length: 1000 }, (_, index) => ({
      id: `message-${index}`,
      role: index % 2 ? 'assistant' as const : 'user' as const,
      content: `Message ${index}`,
      citations: [],
      sequenceNumber: index + 1,
      action: null,
    }));
    mockUseConversation.mockReturnValue({
      data: { ...conversation, messages }, error: undefined, isError: false,
      isFetching: false, isLoading: false, refetch: mockRefetch,
    });
    const screen = await renderScreen();
    const transcript = screen.getByTestId('conversation-transcript');
    expect(transcript.props.data).toHaveLength(1000);
    expect(transcript.props.initialNumToRender).toBe(12);
    expect(transcript.props.windowSize).toBe(7);
    await act(() => transcript.props.refreshControl.props.onRefresh());
    expect(mockRefetch).toHaveBeenCalled();
  });

  it('recovers safely from an inaccessible route and restores list filters', async () => {
    mockUseConversation.mockReturnValue({
      data: undefined,
      error: { status: 404, message: 'raw server detail' },
      isError: true,
      isFetching: false,
      isLoading: false,
      refetch: mockRefetch,
    });
    const screen = await renderScreen();
    expect(screen.getByText('This conversation is no longer available or belongs to another workspace.')).toBeTruthy();
    expect(screen.queryByText('raw server detail')).toBeNull();
    await fireEvent.press(screen.getByRole('button', { name: 'Back to Conversations' }));
    expect(mockReplace).toHaveBeenCalledWith({
      pathname: '/conversations',
      params: { status: 'CLOSED', channel: 'CUSTOM_API' },
    });
  });

  it('returns inaccessible conversation routes to their originating ticket', async () => {
    mockRouteParams = {
      conversationId: 'conversation-1', returnTicketId: 'ticket-1',
      returnTicketStatus: 'IN_PROGRESS', returnTicketPriority: 'URGENT',
      returnTicketSource: 'CUSTOM_API', returnTicketAssignee: 'unassigned',
    };
    mockUseConversation.mockReturnValue({
      data: undefined, error: { status: 404 }, isError: true,
      isFetching: false, isLoading: false, refetch: mockRefetch,
    });
    const screen = await renderScreen();
    await fireEvent.press(screen.getByRole('button', { name: 'Back to Ticket' }));
    expect(mockReplace).toHaveBeenCalledWith({
      pathname: '/tickets/[ticketId]',
      params: {
        ticketId: 'ticket-1', status: 'IN_PROGRESS', priority: 'URGENT',
        source: 'CUSTOM_API', assignee: 'unassigned',
      },
    });
  });
});

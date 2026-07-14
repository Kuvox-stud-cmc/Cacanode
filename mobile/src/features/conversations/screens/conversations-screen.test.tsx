import { act, fireEvent, render } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ConversationsScreen } from '@/features/conversations/screens/conversations-screen';
import type { ConversationListItem } from '@/features/conversations/types';

const mockPush = jest.fn();
const mockSetParams = jest.fn();
const mockRefetch = jest.fn();
const mockUseList = jest.fn();
const mockLoadPage = jest.fn();
let mockRouteParams: Record<string, string> = { status: 'OPEN', channel: 'WIDGET' };

jest.mock('expo-router', () => ({
  router: {
    push: (...args: unknown[]) => mockPush(...args),
    setParams: (...args: unknown[]) => mockSetParams(...args),
  },
  useLocalSearchParams: () => mockRouteParams,
}));

jest.mock('@/features/conversations/api/conversations-api', () => ({
  CONVERSATION_PAGE_SIZE: 50,
  useListConversationsQuery: (...args: unknown[]) => mockUseList(...args),
  useLazyListConversationsQuery: () => [mockLoadPage],
}));

const item: ConversationListItem = {
  id: 'conversation-1', channel: 'WIDGET', status: 'OPEN', messageCount: 3,
  customer: { name: 'Ada', email: 'ada@example.com', externalId: 'external-1' },
  createdAt: '2026-07-14T08:00:00Z', updatedAt: '2026-07-14T08:05:00Z', closedAt: null,
};

async function renderScreen() {
  return render(
    <SafeAreaProvider initialMetrics={{
      frame: { x: 0, y: 0, width: 390, height: 844 },
      insets: { top: 0, right: 0, bottom: 0, left: 0 },
    }}>
      <ConversationsScreen />
    </SafeAreaProvider>,
  );
}

describe('ConversationsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRouteParams = { status: 'OPEN', channel: 'WIDGET' };
    mockLoadPage.mockReturnValue({ unwrap: () => Promise.resolve([]) });
    mockUseList.mockReturnValue({
      data: [item], isError: false, isFetching: false, isLoading: false, refetch: mockRefetch,
    });
  });

  it('queries restored filters and carries them into detail navigation', async () => {
    const screen = await renderScreen();
    expect(mockUseList).toHaveBeenCalledWith({ status: 'OPEN', channel: 'WIDGET', limit: 50, offset: 0 });
    await fireEvent.press(screen.getByRole('button', { name: 'Open conversation with Ada' }));
    expect(mockPush).toHaveBeenCalledWith({
      pathname: '/conversations/[conversationId]',
      params: { conversationId: 'conversation-1', status: 'OPEN', channel: 'WIDGET' },
    });
  });

  it('supports pull refresh and an explicit empty state', async () => {
    const screen = await renderScreen();
    const list = screen.getByTestId('conversation-list');
    await act(() => list.props.refreshControl.props.onRefresh());
    expect(mockRefetch).toHaveBeenCalled();

    mockUseList.mockReturnValue({
      data: [], isError: false, isFetching: false, isLoading: false, refetch: mockRefetch,
    });
    const empty = await renderScreen();
    expect(empty.getByText('No conversations')).toBeTruthy();
  });

  it('shows an explicit retry when the first page fails', async () => {
    mockUseList.mockReturnValue({
      data: undefined, isError: true, isFetching: false, isLoading: false, refetch: mockRefetch,
    });
    const screen = await renderScreen();
    expect(screen.getByText('Unable to load conversations')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Try again' }));
    expect(mockRefetch).toHaveBeenCalled();
  });
});

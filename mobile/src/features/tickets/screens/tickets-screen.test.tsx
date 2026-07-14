import { act, fireEvent, render } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { TicketsScreen } from '@/features/tickets/screens/tickets-screen';
import type { Ticket, TicketPage } from '@/features/tickets/types';

const mockPush = jest.fn();
const mockSetParams = jest.fn();
const mockRefetch = jest.fn();
const mockAssigneeRefetch = jest.fn();
const mockUseList = jest.fn();
const mockUseAssignees = jest.fn();
const mockLoadPage = jest.fn();
let mockRouteParams: Record<string, string> = {
  status: 'IN_PROGRESS', priority: 'HIGH', source: 'CUSTOM_API',
  assignee: '10000000-0000-4000-8000-000000000001',
};

jest.mock('expo-router', () => ({
  router: {
    push: (...args: unknown[]) => mockPush(...args),
    setParams: (...args: unknown[]) => mockSetParams(...args),
  },
  useLocalSearchParams: () => mockRouteParams,
}));

jest.mock('@/features/tickets/api/tickets-api', () => ({
  TICKET_PAGE_SIZE: 50,
  useListTicketsQuery: (...args: unknown[]) => mockUseList(...args),
  useLazyListTicketsQuery: () => [mockLoadPage],
  useListTicketAssigneesQuery: () => mockUseAssignees(),
}));

const ticket: Ticket = {
  id: 'ticket-1', chatbotId: 'bot-1', sessionId: 'conversation-1', externalUserId: 'external-1',
  customerName: 'Ada', customerEmail: 'ada@example.com', source: 'CUSTOM_API',
  title: 'Refund request', description: 'Customer needs help.', status: 'IN_PROGRESS', priority: 'HIGH',
  assignedTo: '10000000-0000-4000-8000-000000000001', assignedToName: 'Grace', resolvedAt: null,
  createdAt: '2026-07-14T08:00:00Z', updatedAt: '2026-07-14T08:05:00Z', notes: [],
};

const page: TicketPage = {
  content: [ticket], number: 0, size: 50, totalElements: 1, totalPages: 1, first: true, last: true,
};

async function renderScreen() {
  return render(
    <SafeAreaProvider initialMetrics={{
      frame: { x: 0, y: 0, width: 390, height: 844 },
      insets: { top: 0, right: 0, bottom: 0, left: 0 },
    }}>
      <TicketsScreen />
    </SafeAreaProvider>,
  );
}

describe('TicketsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRouteParams = {
      status: 'IN_PROGRESS', priority: 'HIGH', source: 'CUSTOM_API',
      assignee: '10000000-0000-4000-8000-000000000001',
    };
    mockUseList.mockReturnValue({
      data: page, isError: false, isFetching: false, isLoading: false, refetch: mockRefetch,
    });
    mockUseAssignees.mockReturnValue({
      data: [{ id: '10000000-0000-4000-8000-000000000001', fullName: 'Grace', email: 'grace@example.com' }],
      isError: false, refetch: mockAssigneeRefetch,
    });
    mockLoadPage.mockReturnValue({ unwrap: () => Promise.resolve({ ...page, content: [], number: 1, last: true }) });
  });

  it('queries restored full filters and preserves them in detail navigation', async () => {
    const screen = await renderScreen();
    expect(mockUseList).toHaveBeenCalledWith({
      status: 'IN_PROGRESS', priority: 'HIGH', source: 'CUSTOM_API',
      assignee: '10000000-0000-4000-8000-000000000001', page: 0, size: 50,
    });
    await fireEvent.press(screen.getByRole('button', { name: 'Open ticket Refund request' }));
    expect(mockPush).toHaveBeenCalledWith({
      pathname: '/tickets/[ticketId]',
      params: {
        ticketId: 'ticket-1', status: 'IN_PROGRESS', priority: 'HIGH', source: 'CUSTOM_API',
        assignee: '10000000-0000-4000-8000-000000000001',
      },
    });
  });

  it('supports pull refresh and explicit empty/error states', async () => {
    const screen = await renderScreen();
    const list = screen.getByTestId('ticket-list');
    await act(() => list.props.refreshControl.props.onRefresh());
    expect(mockRefetch).toHaveBeenCalled();
    expect(mockAssigneeRefetch).toHaveBeenCalled();

    mockUseList.mockReturnValue({ data: { ...page, content: [] }, isError: false, isFetching: false, isLoading: false, refetch: mockRefetch });
    const empty = await renderScreen();
    expect(empty.getByText('No tickets')).toBeTruthy();

    mockUseList.mockReturnValue({ data: undefined, isError: true, isFetching: false, isLoading: false, refetch: mockRefetch });
    const failed = await renderScreen();
    expect(failed.getByText('Unable to load tickets')).toBeTruthy();
    await fireEvent.press(failed.getByRole('button', { name: 'Try again' }));
    expect(mockRefetch).toHaveBeenCalled();
  });
});

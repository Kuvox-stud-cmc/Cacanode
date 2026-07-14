import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { TicketDetailScreen } from '@/features/tickets/screens/ticket-detail-screen';
import type { Ticket } from '@/features/tickets/types';

const mockPush = jest.fn();
const mockReplace = jest.fn();
const mockRefetch = jest.fn();
const mockAssigneeRefetch = jest.fn();
const mockUpdate = jest.fn();
const mockAddNote = jest.fn();
const mockUseTicket = jest.fn();
let mockRouteParams: Record<string, string> = {
  ticketId: 'ticket-1', status: 'OPEN', priority: 'HIGH', source: 'WIDGET', assignee: 'unassigned',
};

jest.mock('expo-router', () => ({
  router: {
    push: (...args: unknown[]) => mockPush(...args),
    replace: (...args: unknown[]) => mockReplace(...args),
  },
  useLocalSearchParams: () => mockRouteParams,
}));

jest.mock('@/features/tickets/api/tickets-api', () => ({
  useGetTicketQuery: () => mockUseTicket(),
  useListTicketAssigneesQuery: () => ({
    data: [{ id: 'user-2', fullName: 'Grace', email: 'grace@example.com' }],
    isError: false,
    refetch: mockAssigneeRefetch,
  }),
  useUpdateTicketMutation: () => [mockUpdate, { isLoading: false }],
  useAddTicketNoteMutation: () => [mockAddNote, { isLoading: false }],
}));

const ticket: Ticket = {
  id: 'ticket-1', chatbotId: 'bot-1', sessionId: 'conversation-1', externalUserId: 'external-1',
  customerName: null, customerEmail: 'customer@example.com', source: 'WIDGET',
  title: 'Refund request', description: 'Customer needs a refund and a detailed follow-up.',
  status: 'OPEN', priority: 'HIGH', assignedTo: null, assignedToName: null, resolvedAt: null,
  createdAt: '2026-07-14T08:00:00Z', updatedAt: '2026-07-14T08:05:00Z',
  notes: [{ id: 'note-1', authorId: 'user-1', authorName: 'Ada', content: 'Initial review', createdAt: '2026-07-14T08:10:00Z' }],
};

async function renderScreen() {
  return render(
    <SafeAreaProvider initialMetrics={{
      frame: { x: 0, y: 0, width: 390, height: 844 },
      insets: { top: 0, right: 0, bottom: 0, left: 0 },
    }}>
      <TicketDetailScreen />
    </SafeAreaProvider>,
  );
}

describe('TicketDetailScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRouteParams = { ticketId: 'ticket-1', status: 'OPEN', priority: 'HIGH', source: 'WIDGET', assignee: 'unassigned' };
    mockRefetch.mockResolvedValue({ data: ticket });
    mockUpdate.mockReturnValue({ unwrap: () => Promise.resolve(ticket) });
    mockAddNote.mockReturnValue({ unwrap: () => Promise.resolve(ticket.notes[0]) });
    mockUseTicket.mockReturnValue({
      data: ticket, error: undefined, isError: false, isFetching: false, isLoading: false, refetch: mockRefetch,
    });
  });

  it('shows customer, description, notes, and opens the Phase 8 conversation with return context', async () => {
    const screen = await renderScreen();
    expect(screen.getAllByText('customer@example.com')).toHaveLength(2);
    expect(screen.getByText('Customer needs a refund and a detailed follow-up.')).toBeTruthy();
    expect(screen.getByText('Initial review')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Open conversation' }));
    expect(mockPush).toHaveBeenCalledWith({
      pathname: '/conversations/[conversationId]',
      params: {
        conversationId: 'conversation-1', returnTicketId: 'ticket-1', returnTicketStatus: 'OPEN',
        returnTicketPriority: 'HIGH', returnTicketSource: 'WIDGET', returnTicketAssignee: 'unassigned',
      },
    });
  });

  it.each([
    ['Resolved', 'RESOLVED', 'Resolve ticket?'],
    ['Closed', 'CLOSED', 'Close ticket?'],
  ] as const)('confirms %s status before sending the mutation', async (label, status, title) => {
    const screen = await renderScreen();
    await fireEvent.press(screen.getByRole('button', { name: 'Change status' }));
    await fireEvent.press(screen.getByRole('button', { name: label }));
    expect(mockUpdate).not.toHaveBeenCalled();
    expect(screen.getByText(title)).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(mockUpdate).toHaveBeenCalledWith({
      ticketId: 'ticket-1', update: { status },
    }));
  });

  it('keeps note text after submission failure and clears only after success', async () => {
    mockAddNote.mockReturnValueOnce({ unwrap: () => Promise.reject({ status: 500, message: 'Unable to save note.' }) });
    const screen = await renderScreen();
    const input = screen.getByLabelText('New internal note');
    await fireEvent.changeText(input, '  Follow up tomorrow  ');
    await fireEvent.press(screen.getByRole('button', { name: 'Add note' }));
    await waitFor(() => expect(screen.getByDisplayValue('  Follow up tomorrow  ')).toBeTruthy());
    expect(screen.getByText('Unable to save note.')).toBeTruthy();

    mockAddNote.mockReturnValueOnce({ unwrap: () => Promise.resolve(ticket.notes[0]) });
    await fireEvent.press(screen.getByRole('button', { name: 'Add note' }));
    await waitFor(() => expect(screen.getByDisplayValue('')).toBeTruthy());
    expect(mockAddNote).toHaveBeenLastCalledWith({ ticketId: 'ticket-1', content: 'Follow up tomorrow' });
  });

  it('recovers from inaccessible tickets and restores list filters', async () => {
    mockUseTicket.mockReturnValue({
      data: undefined, error: { status: 404, message: 'raw detail' }, isError: true,
      isFetching: false, isLoading: false, refetch: mockRefetch,
    });
    const screen = await renderScreen();
    expect(screen.getByText('This ticket is no longer available or belongs to another workspace.')).toBeTruthy();
    expect(screen.queryByText('raw detail')).toBeNull();
    await fireEvent.press(screen.getByRole('button', { name: 'Back to Tickets' }));
    expect(mockReplace).toHaveBeenCalledWith({
      pathname: '/tickets',
      params: { status: 'OPEN', priority: 'HIGH', source: 'WIDGET', assignee: 'unassigned' },
    });
  });
});

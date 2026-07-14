import { springApi } from '@/services/api/api';
import type {
  Ticket,
  TicketAssignee,
  TicketListRequest,
  TicketNote,
  TicketPage,
  TicketUpdate,
} from '@/features/tickets/types';

export const TICKET_PAGE_SIZE = 50;

type TicketPageResponse = Omit<TicketPage, 'content'> & {
  content: (Omit<Ticket, 'notes'> & { notes?: TicketNote[] | null })[];
};

function mapTicket(ticket: Omit<Ticket, 'notes'> & { notes?: TicketNote[] | null }): Ticket {
  return { ...ticket, notes: ticket.notes ?? [] };
}

function mapPage(page: TicketPageResponse): TicketPage {
  return { ...page, content: page.content.map(mapTicket) };
}

export const ticketsApi = springApi.injectEndpoints({
  endpoints: (build) => ({
    listTickets: build.query<TicketPage, TicketListRequest>({
      query: ({ assignee, page, priority, size = TICKET_PAGE_SIZE, source, status }) => ({
        url: '/tenants/me/tickets',
        params: {
          page,
          size,
          ...(status ? { status } : {}),
          ...(priority ? { priority } : {}),
          ...(source ? { source } : {}),
          ...(assignee === 'unassigned' ? { unassigned: true } : {}),
          ...(assignee && assignee !== 'unassigned' ? { assignedTo: assignee } : {}),
        },
      }),
      transformResponse: (response: TicketPageResponse) => mapPage(response),
      providesTags: (result) => [
        { type: 'Ticket', id: 'LIST' },
        ...(result?.content ?? []).map((ticket) => ({ type: 'Ticket' as const, id: ticket.id })),
      ],
    }),
    getTicket: build.query<Ticket, string>({
      query: (ticketId) => `/tenants/me/tickets/${ticketId}`,
      transformResponse: (response: Omit<Ticket, 'notes'> & { notes?: TicketNote[] | null }) =>
        mapTicket(response),
      providesTags: (_result, _error, ticketId) => [{ type: 'Ticket', id: ticketId }],
    }),
    listTicketAssignees: build.query<TicketAssignee[], void>({
      query: () => '/tenants/me/tickets/assignees',
      providesTags: [{ type: 'Ticket', id: 'ASSIGNEES' }],
    }),
    updateTicket: build.mutation<Ticket, { ticketId: string; update: TicketUpdate }>({
      query: ({ ticketId, update }) => ({
        url: `/tenants/me/tickets/${ticketId}`,
        method: 'PATCH',
        body: update,
      }),
      transformResponse: (response: Omit<Ticket, 'notes'> & { notes?: TicketNote[] | null }) =>
        mapTicket(response),
      invalidatesTags: (_result, _error, { ticketId }) => [
        { type: 'Ticket', id: ticketId },
        { type: 'Ticket', id: 'LIST' },
        { type: 'Ticket', id: 'ASSIGNEES' },
        { type: 'Dashboard' },
      ],
    }),
    addTicketNote: build.mutation<TicketNote, { ticketId: string; content: string }>({
      query: ({ content, ticketId }) => ({
        url: `/tenants/me/tickets/${ticketId}/notes`,
        method: 'POST',
        body: { content },
      }),
      invalidatesTags: (_result, _error, { ticketId }) => [
        { type: 'Ticket', id: ticketId },
        { type: 'Ticket', id: 'LIST' },
        { type: 'Dashboard' },
      ],
    }),
  }),
});

export const {
  useAddTicketNoteMutation,
  useGetTicketQuery,
  useLazyListTicketsQuery,
  useListTicketAssigneesQuery,
  useListTicketsQuery,
  useUpdateTicketMutation,
} = ticketsApi;

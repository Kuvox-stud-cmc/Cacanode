import {
  mergeTicketPages,
  ticketCustomerIdentity,
  ticketFiltersFromRoute,
  ticketFiltersToRoute,
  ticketSourceLabel,
  ticketStatusLabel,
  validateTicketNote,
} from '@/features/tickets/model/ticket-state';
import type { Ticket, TicketPage } from '@/features/tickets/types';

describe('ticket route and paging state', () => {
  it('accepts valid full filters and rejects malformed route values', () => {
    expect(ticketFiltersFromRoute({
      status: 'IN_PROGRESS', priority: 'HIGH', source: 'CUSTOM_API',
      assignee: '10000000-0000-4000-8000-000000000001',
    })).toEqual({
      status: 'IN_PROGRESS', priority: 'HIGH', source: 'CUSTOM_API',
      assignee: '10000000-0000-4000-8000-000000000001',
    });
    expect(ticketFiltersFromRoute({ status: 'UNKNOWN', priority: 'MAX', source: 'EMAIL', assignee: 'bad' }))
      .toEqual({});
    expect(ticketFiltersFromRoute({ assignee: 'unassigned' })).toEqual({ assignee: 'unassigned' });
    expect(ticketFiltersToRoute({ status: 'CLOSED' })).toEqual({
      status: 'CLOSED', priority: undefined, source: undefined, assignee: undefined,
    });
  });

  it('merges Spring pages without duplicate ticket IDs', () => {
    const page = (ids: string[]) => ({ content: ids.map((id) => ({ id } as Ticket)) } as TicketPage);
    expect(mergeTicketPages([page(['1', '2']), page(['2', '3'])]).map(({ id }) => id))
      .toEqual(['1', '2', '3']);
  });
});

describe('ticket display and note rules', () => {
  it('uses safe customer fallbacks and readable labels', () => {
    expect(ticketCustomerIdentity({ customerName: ' Ada ', customerEmail: 'ada@example.com', externalUserId: 'ext' } as Ticket)).toBe('Ada');
    expect(ticketCustomerIdentity({ customerName: null, customerEmail: 'ada@example.com', externalUserId: 'ext' } as Ticket)).toBe('ada@example.com');
    expect(ticketCustomerIdentity({ customerName: null, customerEmail: '', externalUserId: 'ext' } as Ticket)).toBe('ext');
    expect(ticketStatusLabel('IN_PROGRESS')).toBe('In progress');
    expect(ticketSourceLabel('CUSTOM_API')).toBe('Custom API');
  });

  it('validates trimmed nonblank notes up to 5,000 characters', () => {
    expect(validateTicketNote('   ')).toContain('Enter a note');
    expect(validateTicketNote('x'.repeat(5001))).toContain('5,000');
    expect(validateTicketNote(' follow up ')).toBeNull();
  });
});

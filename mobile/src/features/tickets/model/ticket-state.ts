import {
  TICKET_PRIORITIES,
  TICKET_SOURCES,
  TICKET_STATUSES,
  type Ticket,
  type TicketFilters,
  type TicketPage,
  type TicketPriority,
  type TicketSource,
  type TicketStatus,
} from '@/features/tickets/types';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function allowed<T extends string>(value: string | undefined, values: readonly T[]) {
  return value && values.includes(value as T) ? value as T : undefined;
}

export function ticketFiltersFromRoute(
  params: Record<string, string | string[] | undefined>,
): TicketFilters {
  const status = allowed(first(params.status), TICKET_STATUSES);
  const priority = allowed(first(params.priority), TICKET_PRIORITIES);
  const source = allowed(first(params.source), TICKET_SOURCES);
  const assigneeValue = first(params.assignee);
  const assignee = assigneeValue === 'unassigned' || UUID_PATTERN.test(assigneeValue ?? '')
    ? assigneeValue
    : undefined;
  return {
    ...(status ? { status: status as TicketStatus } : {}),
    ...(priority ? { priority: priority as TicketPriority } : {}),
    ...(source ? { source: source as TicketSource } : {}),
    ...(assignee ? { assignee } : {}),
  };
}

export function ticketFiltersToRoute(filters: TicketFilters) {
  return {
    status: filters.status || undefined,
    priority: filters.priority || undefined,
    source: filters.source || undefined,
    assignee: filters.assignee || undefined,
  };
}

export function mergeTicketPages(pages: TicketPage[]) {
  const seen = new Set<string>();
  return pages.flatMap((page) => page.content.filter((ticket) => {
    if (seen.has(ticket.id)) return false;
    seen.add(ticket.id);
    return true;
  }));
}

export function ticketCustomerIdentity(ticket: Pick<
  Ticket,
  'customerName' | 'customerEmail' | 'externalUserId'
>) {
  return ticket.customerName?.trim()
    || ticket.customerEmail?.trim()
    || ticket.externalUserId?.trim()
    || 'Customer';
}

export function ticketStatusLabel(status: TicketStatus) {
  return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (value) => value.toUpperCase());
}

export function ticketSourceLabel(source: TicketSource) {
  return source === 'CUSTOM_API' ? 'Custom API' : 'Widget';
}

export function validateTicketNote(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return 'Enter a note before submitting.';
  if (trimmed.length > 5000) return 'Notes must be 5,000 characters or fewer.';
  return null;
}

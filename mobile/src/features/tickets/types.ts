export const TICKET_STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as const;
export const TICKET_PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
export const TICKET_SOURCES = ['WIDGET', 'CUSTOM_API'] as const;

export type TicketStatus = (typeof TICKET_STATUSES)[number];
export type TicketPriority = (typeof TICKET_PRIORITIES)[number];
export type TicketSource = (typeof TICKET_SOURCES)[number];

export type TicketNote = {
  id: string;
  authorId: string;
  authorName: string;
  content: string;
  createdAt: string;
};

export type TicketAssignee = {
  id: string;
  fullName: string;
  email: string;
};

export type Ticket = {
  id: string;
  chatbotId: string;
  sessionId: string;
  externalUserId: string | null;
  customerName: string | null;
  customerEmail: string;
  source: TicketSource;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  assignedTo: string | null;
  assignedToName: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
  notes: TicketNote[];
};

export type TicketPage = {
  content: Ticket[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type TicketFilters = {
  status?: TicketStatus;
  priority?: TicketPriority;
  source?: TicketSource;
  assignee?: string | 'unassigned';
};

export type TicketListRequest = TicketFilters & {
  page: number;
  size?: number;
};

export type TicketUpdate = {
  status?: TicketStatus;
  priority?: TicketPriority;
  assignedTo?: string;
  clearAssignee?: boolean;
};

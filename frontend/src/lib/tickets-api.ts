import { getApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
export type TicketPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";

export type Ticket = {
  id: string;
  chatbotId: string;
  sessionId: string;
  externalUserId: string | null;
  customerName: string | null;
  customerEmail: string;
  source: "WIDGET" | "CUSTOM_API";
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

export type TicketNote = {
  id: string;
  authorId: string;
  authorName: string;
  content: string;
  createdAt: string;
};

export type Assignee = { id: string; fullName: string; email: string };

export async function listTickets(request: ApiRequest, query: {
  status?: TicketStatus; priority?: TicketPriority; source?: string; assignedTo?: string;
  unassigned?: boolean; q?: string; createdFrom?: string; createdTo?: string;
  sort?: string; direction?: "asc" | "desc"; page?: number; size?: number; signal?: AbortSignal;
} = {}): Promise<{ items: Ticket[]; total: number }> {
  const params = new URLSearchParams({ page: String(query.page ?? 0), size: String(query.size ?? 20) });
  if (query.status) params.set("status", query.status);
  if (query.priority) params.set("priority", query.priority);
  if (query.source) params.set("source", query.source);
  if (query.assignedTo) params.set("assignedTo", query.assignedTo);
  if (query.unassigned) params.set("unassigned", "true");
  if (query.q) params.set("q", query.q.slice(0, 200));
  if (query.createdFrom) params.set("created_from", query.createdFrom);
  if (query.createdTo) params.set("created_to", query.createdTo);
  if (query.sort) params.set("sort", query.sort);
  if (query.direction) params.set("direction", query.direction);
  const page = await readJsonOrThrow<{ content: Ticket[]; totalElements: number }>(
    await request(`${getApiBase()}/tenants/me/tickets?${params}`, { signal: query.signal }),
  );
  return { items: page.content, total: page.totalElements };
}

export async function getTicket(request: ApiRequest, id: string): Promise<Ticket> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/tickets/${id}`));
}

export async function updateTicket(
  request: ApiRequest,
  id: string,
  payload: { status?: TicketStatus; priority?: TicketPriority; assignedTo?: string; clearAssignee?: boolean },
): Promise<Ticket> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/tickets/${id}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  }));
}

export async function addTicketNote(request: ApiRequest, id: string, content: string): Promise<TicketNote> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/tickets/${id}/notes`, {
    method: "POST",
    body: JSON.stringify({ content }),
  }));
}

export async function listTicketAssignees(request: ApiRequest): Promise<Assignee[]> {
  return readJsonOrThrow(await request(`${getApiBase()}/tenants/me/tickets/assignees`));
}

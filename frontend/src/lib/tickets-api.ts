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

export async function listTickets(request: ApiRequest, status?: TicketStatus): Promise<Ticket[]> {
  const params = new URLSearchParams({ size: "100", sort: "createdAt,desc" });
  if (status) params.set("status", status);
  const page = await readJsonOrThrow<{ content: Ticket[] }>(
    await request(`${getApiBase()}/tenants/me/tickets?${params}`),
  );
  return page.content;
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

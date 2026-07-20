import { getApiBase } from "@/lib/auth-api";
import { parseApiError, readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

export type CustomerConversation = {
  id: string;
  channel: "WIDGET" | "CUSTOM_API";
  external_user_id: string | null;
  customer_name: string | null;
  customer_email: string | null;
  status: "OPEN" | "CLOSED";
  message_count: number;
  created_at: string;
  updated_at: string;
  closed_at: string | null;
};

export type CustomerConversationDetail = CustomerConversation & {
  customer_metadata: Record<string, unknown>;
  messages: Array<{ role: string; content: string; sequence_number: number; action?: unknown }>;
};

export async function listCustomerConversations(
  request: ApiRequest,
  query: {
    status?: "OPEN" | "CLOSED"; channel?: string; q?: string; startedFrom?: string;
    startedTo?: string; sort?: string; direction?: "asc" | "desc";
    offset?: number; limit?: number; signal?: AbortSignal;
  } = {},
): Promise<{ items: CustomerConversation[]; total: number }> {
  const params = new URLSearchParams({ limit: String(query.limit ?? 20), offset: String(query.offset ?? 0) });
  if (query.status) params.set("conversation_status", query.status);
  if (query.channel) params.set("channel", query.channel);
  if (query.q) params.set("q", query.q.slice(0, 200));
  if (query.startedFrom) params.set("started_from", query.startedFrom);
  if (query.startedTo) params.set("started_to", query.startedTo);
  if (query.sort) params.set("sort", query.sort);
  if (query.direction) params.set("direction", query.direction);
  const response = await request(`${getApiBase()}/chat/conversations?${params}`, { signal: query.signal });
  const items = await readJsonOrThrow<CustomerConversation[]>(response);
  return { items, total: Number(response.headers.get("X-Total-Count") ?? items.length) };
}

export async function getCustomerConversation(
  request: ApiRequest,
  id: string,
): Promise<CustomerConversationDetail> {
  return readJsonOrThrow(await request(`${getApiBase()}/chat/conversations/${id}`));
}

export async function closeCustomerConversation(request: ApiRequest, id: string): Promise<void> {
  const response = await request(`${getApiBase()}/chat/conversations/${id}`, { method: "DELETE" });
  if (!response.ok) throw await parseApiError(response);
}

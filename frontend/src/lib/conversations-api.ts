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
  status?: "OPEN" | "CLOSED",
): Promise<CustomerConversation[]> {
  const params = new URLSearchParams({ limit: "100" });
  if (status) params.set("conversation_status", status);
  return readJsonOrThrow(await request(`${getApiBase()}/chat/conversations?${params}`));
}

export async function getCustomerConversation(
  request: ApiRequest,
  id: string,
): Promise<CustomerConversationDetail> {
  return readJsonOrThrow(await request(`${getApiBase()}/chat/conversations/${id}`));
}

export async function closeCustomerConversation(request: ApiRequest, id: string): Promise<void> {
  const response = await request(`${getApiBase()}/chat/sessions/${id}`, { method: "DELETE" });
  if (!response.ok) throw await parseApiError(response);
}

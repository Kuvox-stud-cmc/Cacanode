import type {
  AssistantMessageResponse,
  ChatHistoryMessageResponse,
  ChatSessionResponse,
  PlaygroundSession,
} from "@/types";
import { getAiApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

const CHAT_MESSAGE_TIMEOUT_MS = 90_000;

export async function createChatSessionApi(
  request: ApiRequest,
  payload: {
    chatbot_id: string;
    knowledge_base_id: string;
    locale: string;
  },
): Promise<ChatSessionResponse> {
  const res = await request(`${getAiApiBase()}/chat/sessions`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
  return readJsonOrThrow<ChatSessionResponse>(res);
}

export async function listPlaygroundSessionsApi(
  request: ApiRequest,
  limit = 50,
  offset = 0,
): Promise<PlaygroundSession[]> {
  const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
  const res = await request(`${getAiApiBase()}/chat/playground/sessions?${params}`);
  return readJsonOrThrow<PlaygroundSession[]>(res);
}

export async function hidePlaygroundSessionApi(
  request: ApiRequest,
  sessionId: string,
): Promise<void> {
  const res = await request(`${getAiApiBase()}/chat/playground/sessions/${sessionId}`, {
    method: "DELETE",
  });
  if (!res.ok) await readJsonOrThrow(res);
}

export async function submitChatMessageApi(
  request: ApiRequest,
  sessionId: string,
  content: string,
  signal?: AbortSignal,
): Promise<AssistantMessageResponse> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), CHAT_MESSAGE_TIMEOUT_MS);
  const abortFromCaller = () => controller.abort();

  if (signal?.aborted) {
    controller.abort();
  } else {
    signal?.addEventListener("abort", abortFromCaller, { once: true });
  }

  try {
    const res = await request(`${getAiApiBase()}/chat/sessions/${sessionId}/messages`, {
      method: "POST",
      body: JSON.stringify({ content }),
      signal: controller.signal,
    });
    return readJsonOrThrow<AssistantMessageResponse>(res);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error("The model is still generating. Try a shorter question or retry in a moment.");
    }
    throw error;
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener("abort", abortFromCaller);
  }
}

export async function getChatMessagesApi(
  request: ApiRequest,
  sessionId: string,
): Promise<ChatHistoryMessageResponse[]> {
  const messages: ChatHistoryMessageResponse[] = [];
  let after = 0;
  while (true) {
    const params = new URLSearchParams({ limit: "200", after: String(after) });
    const res = await request(`${getAiApiBase()}/chat/sessions/${sessionId}/messages?${params}`);
    const page = await readJsonOrThrow<ChatHistoryMessageResponse[]>(res);
    messages.push(...page);
    if (page.length < 200) return messages;
    after = page.at(-1)?.sequence_number ?? after;
  }
}

import type { AssistantMessageResponse, ChatSessionResponse } from "@/types";
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

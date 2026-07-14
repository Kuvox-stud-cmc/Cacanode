import type { ApiError } from '@/services/api/errors';
import type {
  AssistantResponse,
  ChatHistoryMessage,
  PlaygroundSession,
  TranscriptMessage,
} from '@/features/chat/types';

export const CHAT_MAX_LENGTH = 32_000;
export const NO_INFORMATION_RESPONSE =
  'Mình không tìm thấy thông tin phù hợp trong tài liệu đã tải lên để trả lời câu hỏi này.';

export type ChatState = {
  selectedSessionId: string | null;
  selectionInitialized: boolean;
  unavailableSessionIds: string[];
  draft: string;
  messages: TranscriptMessage[];
  historyRequestId: string | null;
  historyStatus: 'idle' | 'loading' | 'ready' | 'failed';
  historyError: string | null;
  activeSendId: string | null;
};

export const initialChatState: ChatState = {
  selectedSessionId: null,
  selectionInitialized: false,
  unavailableSessionIds: [],
  draft: '',
  messages: [],
  historyRequestId: null,
  historyStatus: 'idle',
  historyError: null,
  activeSendId: null,
};

export type ChatAction =
  | { type: 'sessionsLoaded'; sessions: PlaygroundSession[] }
  | { type: 'draftChanged'; draft: string }
  | { type: 'newChat' }
  | { type: 'sessionSelected'; sessionId: string }
  | { type: 'historyStarted'; sessionId: string; requestId: string }
  | {
      type: 'historySucceeded';
      sessionId: string;
      requestId: string;
      messages: ChatHistoryMessage[];
    }
  | {
      type: 'historyFailed';
      sessionId: string;
      requestId: string;
      message: string;
      inaccessible: boolean;
    }
  | { type: 'sendStarted'; sendId: string; userId: string; content: string }
  | { type: 'sessionCreated'; sendId: string; sessionId: string }
  | { type: 'sendSucceeded'; sendId: string; response: AssistantResponse }
  | { type: 'sendFailed'; sendId: string; message: string }
  | { type: 'sessionHidden'; sessionId: string; nextSessionId: string | null };

export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'sessionsLoaded': {
      if (state.selectionInitialized) return state;
      const firstAvailable = action.sessions.find(
        (session) => !state.unavailableSessionIds.includes(session.id),
      );
      return {
        ...state,
        selectedSessionId: firstAvailable?.id ?? null,
        selectionInitialized: true,
      };
    }
    case 'draftChanged':
      return { ...state, draft: action.draft.slice(0, CHAT_MAX_LENGTH) };
    case 'newChat':
      return {
        ...state,
        selectedSessionId: null,
        selectionInitialized: true,
        draft: '',
        messages: [],
        historyRequestId: null,
        historyStatus: 'idle',
        historyError: null,
      };
    case 'sessionSelected':
      return {
        ...state,
        selectedSessionId: action.sessionId,
        selectionInitialized: true,
        messages: [],
        historyRequestId: null,
        historyStatus: 'idle',
        historyError: null,
      };
    case 'historyStarted':
      if (action.sessionId !== state.selectedSessionId) return state;
      return {
        ...state,
        messages: [],
        historyRequestId: action.requestId,
        historyStatus: 'loading',
        historyError: null,
      };
    case 'historySucceeded':
      if (
        action.sessionId !== state.selectedSessionId ||
        action.requestId !== state.historyRequestId
      ) {
        return state;
      }
      return {
        ...state,
        messages: visibleTranscript(action.sessionId, action.messages),
        historyStatus: 'ready',
        historyError: null,
      };
    case 'historyFailed':
      if (
        action.sessionId !== state.selectedSessionId ||
        action.requestId !== state.historyRequestId
      ) {
        return state;
      }
      if (action.inaccessible) {
        return {
          ...state,
          selectedSessionId: null,
          messages: [],
          historyRequestId: null,
          historyStatus: 'idle',
          historyError: null,
          unavailableSessionIds: state.unavailableSessionIds.includes(action.sessionId)
            ? state.unavailableSessionIds
            : [...state.unavailableSessionIds, action.sessionId],
        };
      }
      return {
        ...state,
        messages: [],
        historyStatus: 'failed',
        historyError: action.message,
      };
    case 'sendStarted':
      return {
        ...state,
        draft: '',
        activeSendId: action.sendId,
        historyStatus: 'ready',
        historyError: null,
        messages: [
          ...state.messages,
          {
            id: action.userId,
            role: 'user',
            content: action.content,
            citations: [],
            status: 'sent',
          },
          {
            id: action.sendId,
            role: 'assistant',
            content: 'Thinking…',
            citations: [],
            status: 'pending',
          },
        ],
      };
    case 'sessionCreated':
      if (action.sendId !== state.activeSendId) return state;
      return { ...state, selectedSessionId: action.sessionId, selectionInitialized: true };
    case 'sendSucceeded':
      if (action.sendId !== state.activeSendId) return state;
      return {
        ...state,
        activeSendId: null,
        messages: replaceAssistant(state.messages, action.sendId, {
          id: action.sendId,
          role: 'assistant',
          content: action.response.content || 'No answer was generated.',
          citations: action.response.citations,
          status: 'sent',
          noInformation: action.response.content === NO_INFORMATION_RESPONSE,
        }),
      };
    case 'sendFailed':
      if (action.sendId !== state.activeSendId) return state;
      return {
        ...state,
        activeSendId: null,
        messages: replaceAssistant(state.messages, action.sendId, {
          id: action.sendId,
          role: 'assistant',
          content: 'I couldn’t confirm an answer for that message.',
          citations: [],
          status: 'failed',
          failureMessage: action.message,
        }),
      };
    case 'sessionHidden':
      if (action.sessionId !== state.selectedSessionId) return state;
      return {
        ...state,
        selectedSessionId: action.nextSessionId,
        messages: [],
        draft: '',
        historyRequestId: null,
        historyStatus: 'idle',
        historyError: null,
      };
  }
}

function visibleTranscript(sessionId: string, messages: ChatHistoryMessage[]): TranscriptMessage[] {
  return messages
    .filter((message) => message.role === 'user' || message.role === 'assistant')
    .map((message, index) => ({
      id: `${sessionId}-${message.sequenceNumber ?? index}`,
      role: message.role as 'user' | 'assistant',
      content: message.content,
      citations: message.citations,
      status: 'sent',
      noInformation: message.content === NO_INFORMATION_RESPONSE,
    }));
}

function replaceAssistant(
  messages: TranscriptMessage[],
  sendId: string,
  replacement: TranscriptMessage,
): TranscriptMessage[] {
  return messages.map((message) => (message.id === sendId ? replacement : message));
}

export function canSendMessage(draft: string, workspaceReady: boolean, sending: boolean): boolean {
  const content = draft.trim();
  return workspaceReady && !sending && content.length > 0 && content.length <= CHAT_MAX_LENGTH;
}

export function isSessionNotFound(error: ApiError | undefined): boolean {
  return error?.status === 404 && error.code === 'SESSION_NOT_FOUND';
}

export function chatFailureMessage(error: ApiError | undefined): string {
  if (error?.code === 'MESSAGE_QUOTA_EXCEEDED') {
    return 'Your workspace has reached its message quota. Ask an administrator to review usage.';
  }
  if (error?.code === 'MODEL_TIMEOUT' || error?.kind === 'timeout') {
    return 'The answer took too long. It may still appear after you reload this conversation.';
  }
  if (error?.code === 'MODEL_PROVIDER_ERROR') {
    return 'The answer service is temporarily unavailable. Reload this conversation before sending again.';
  }
  if (error?.code === 'CHAT_SESSION_STORE_UNAVAILABLE') {
    return 'Conversation storage is temporarily unavailable. Your local transcript has not been saved.';
  }
  if (error?.code === 'WORKSPACE_NOT_FOUND') {
    return 'Your chat workspace is unavailable. Ask an administrator to verify the workspace setup.';
  }
  if (error?.kind === 'network') {
    return 'The connection was interrupted. Reload this conversation before sending another message.';
  }
  return 'The message could not be completed. Reload this conversation to check its server status.';
}

export function workspaceFailureMessage(error: ApiError | undefined): string {
  if (error?.kind === 'network' || error?.kind === 'timeout') return error.message;
  return 'Your employee chat workspace is temporarily unavailable. Please try again.';
}

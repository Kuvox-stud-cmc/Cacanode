import type { ApiError } from '@/services/api/errors';
import { aiApi } from '@/services/api/api';
import type {
  AssistantResponse,
  ChatCitation,
  ChatHistoryMessage,
  ChatSession,
  PlaygroundSession,
  TenantWorkspace,
} from '@/features/chat/types';

export const CHAT_MESSAGE_TIMEOUT_MS = 90_000;
export const CHAT_HISTORY_PAGE_SIZE = 200;
export const PLAYGROUND_HISTORY_LIMIT = 50;

type CitationResponse = {
  id: string;
  document_id: string;
  source_name: string;
  page_number: number | null;
  chunk_index: number;
  score: number;
  snippet: string;
  unit_id?: string | null;
  modality?: string | null;
  section_path?: string[];
  block_type?: string | null;
  sheet_name?: string | null;
  cell_range?: string | null;
  table_id?: string | null;
};

type ChatSessionResponse = {
  id: string;
  chatbot_id: string;
  knowledge_base_id: string;
  tenant_id: string;
  locale: string;
};

type AssistantMessageResponse = {
  role: 'assistant';
  content: string;
  citations?: CitationResponse[];
  action?: Record<string, unknown> | null;
};

type HistoryMessageResponse = {
  role: 'user' | 'assistant' | 'system';
  content: string;
  citations?: CitationResponse[];
  sequence_number?: number | null;
  action?: Record<string, unknown> | null;
};

type PlaygroundSessionResponse = {
  id: string;
  title: string;
  message_count: number;
  status: string;
  created_at: string;
  last_activity_at: string;
};

function mapCitation(citation: CitationResponse): ChatCitation {
  return {
    id: citation.id,
    documentId: citation.document_id,
    sourceName: citation.source_name,
    pageNumber: citation.page_number,
    chunkIndex: citation.chunk_index,
    score: citation.score,
    snippet: citation.snippet,
    unitId: citation.unit_id ?? null,
    modality: citation.modality ?? null,
    sectionPath: citation.section_path ?? [],
    blockType: citation.block_type ?? null,
    sheetName: citation.sheet_name ?? null,
    cellRange: citation.cell_range ?? null,
    tableId: citation.table_id ?? null,
  };
}

function mapSession(session: ChatSessionResponse): ChatSession {
  return {
    id: session.id,
    chatbotId: session.chatbot_id,
    knowledgeBaseId: session.knowledge_base_id,
    tenantId: session.tenant_id,
    locale: session.locale,
  };
}

function mapAssistant(response: AssistantMessageResponse): AssistantResponse {
  return {
    role: response.role,
    content: response.content,
    citations: (response.citations ?? []).map(mapCitation),
    action: response.action ?? null,
  };
}

function mapHistoryMessage(message: HistoryMessageResponse): ChatHistoryMessage {
  return {
    role: message.role,
    content: message.content,
    citations: (message.citations ?? []).map(mapCitation),
    sequenceNumber: message.sequence_number ?? null,
    action: message.action ?? null,
  };
}

function mapPlaygroundSession(session: PlaygroundSessionResponse): PlaygroundSession {
  return {
    id: session.id,
    title: session.title,
    messageCount: session.message_count,
    status: session.status,
    createdAt: session.created_at,
    lastActivityAt: session.last_activity_at,
  };
}

export const chatApi = aiApi.injectEndpoints({
  endpoints: (build) => ({
    createChatSession: build.mutation<ChatSession, TenantWorkspace>({
      query: (workspace) => ({
        url: '/chat/sessions',
        method: 'POST',
        body: {
          chatbot_id: workspace.chatbot.id,
          knowledge_base_id: workspace.knowledgeBase.id,
          locale: workspace.chatbot.defaultLocale || workspace.knowledgeBase.defaultLocale,
        },
      }),
      transformResponse: (response: ChatSessionResponse) => mapSession(response),
      invalidatesTags: [{ type: 'Chat', id: 'LIST' }],
    }),
    submitChatMessage: build.mutation<AssistantResponse, { sessionId: string; content: string }>({
      query: ({ content, sessionId }) => ({
        url: `/chat/sessions/${sessionId}/messages`,
        method: 'POST',
        body: { content },
        timeout: CHAT_MESSAGE_TIMEOUT_MS,
      }),
      transformResponse: (response: AssistantMessageResponse) => mapAssistant(response),
      invalidatesTags: [{ type: 'Chat', id: 'LIST' }],
    }),
    getChatHistory: build.query<ChatHistoryMessage[], string>({
      async queryFn(sessionId, _api, _extraOptions, baseQuery) {
        const messages: ChatHistoryMessage[] = [];
        let after = 0;

        while (true) {
          const result = await baseQuery({
            url: `/chat/sessions/${sessionId}/messages`,
            params: { limit: CHAT_HISTORY_PAGE_SIZE, after },
          });
          if (result.error) return { error: result.error as ApiError };

          const page = (result.data as HistoryMessageResponse[]).map(mapHistoryMessage);
          messages.push(...page);
          if (page.length < CHAT_HISTORY_PAGE_SIZE) return { data: messages };

          const nextAfter = page.at(-1)?.sequenceNumber;
          if (nextAfter === null || nextAfter === undefined || nextAfter <= after) {
            return { data: messages };
          }
          after = nextAfter;
        }
      },
      providesTags: (_result, _error, sessionId) => [{ type: 'Chat', id: sessionId }],
    }),
    listPlaygroundSessions: build.query<PlaygroundSession[], void>({
      query: () => ({
        url: '/chat/playground/sessions',
        params: { limit: PLAYGROUND_HISTORY_LIMIT, offset: 0 },
      }),
      transformResponse: (response: PlaygroundSessionResponse[]) => response.map(mapPlaygroundSession),
      providesTags: [{ type: 'Chat', id: 'LIST' }],
    }),
    hidePlaygroundSession: build.mutation<void, string>({
      query: (sessionId) => ({
        url: `/chat/playground/sessions/${sessionId}`,
        method: 'DELETE',
      }),
      transformResponse: () => undefined,
      invalidatesTags: [{ type: 'Chat', id: 'LIST' }],
    }),
  }),
});

export const {
  useCreateChatSessionMutation,
  useGetChatHistoryQuery,
  useHidePlaygroundSessionMutation,
  useLazyGetChatHistoryQuery,
  useListPlaygroundSessionsQuery,
  useSubmitChatMessageMutation,
} = chatApi;

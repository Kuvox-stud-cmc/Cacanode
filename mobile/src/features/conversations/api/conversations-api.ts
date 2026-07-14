import { aiApi } from '@/services/api/api';
import type {
  ConversationCitation,
  ConversationDetail,
  ConversationListItem,
  ConversationListRequest,
  ConversationMessage,
} from '@/features/conversations/types';

export const CONVERSATION_PAGE_SIZE = 50;

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

type ConversationListItemResponse = {
  id: string;
  channel: ConversationListItem['channel'];
  external_user_id: string | null;
  customer_name: string | null;
  customer_email: string | null;
  status: ConversationListItem['status'];
  message_count: number;
  created_at: string;
  updated_at: string;
  closed_at: string | null;
};

type ConversationMessageResponse = {
  role: string;
  content: string;
  citations?: CitationResponse[];
  sequence_number?: number | null;
  action?: Record<string, unknown> | null;
};

type ConversationDetailResponse = Omit<ConversationListItemResponse, 'message_count'> & {
  customer_metadata?: Record<string, unknown> | null;
  messages: ConversationMessageResponse[];
};

function mapCitation(citation: CitationResponse): ConversationCitation {
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

function roleFromResponse(role: string): ConversationMessage['role'] {
  return role === 'user' || role === 'assistant' || role === 'system' ? role : 'system';
}

function mapMessage(message: ConversationMessageResponse, index: number): ConversationMessage {
  const sequenceNumber = message.sequence_number ?? null;
  return {
    id: sequenceNumber === null ? `message-${index}` : `message-${sequenceNumber}`,
    role: roleFromResponse(message.role),
    content: message.content,
    citations: (message.citations ?? []).map(mapCitation),
    sequenceNumber,
    action: message.action ?? null,
  };
}

function mapListItem(response: ConversationListItemResponse): ConversationListItem {
  return {
    id: response.id,
    channel: response.channel,
    customer: {
      externalId: response.external_user_id,
      name: response.customer_name,
      email: response.customer_email,
    },
    status: response.status,
    messageCount: response.message_count,
    createdAt: response.created_at,
    updatedAt: response.updated_at,
    closedAt: response.closed_at,
  };
}

function mapDetail(response: ConversationDetailResponse): ConversationDetail {
  return {
    id: response.id,
    channel: response.channel,
    customer: {
      externalId: response.external_user_id,
      name: response.customer_name,
      email: response.customer_email,
      metadata: response.customer_metadata ?? {},
    },
    status: response.status,
    createdAt: response.created_at,
    updatedAt: response.updated_at,
    closedAt: response.closed_at,
    messages: response.messages.map(mapMessage),
  };
}

export const conversationsApi = aiApi.injectEndpoints({
  endpoints: (build) => ({
    listConversations: build.query<ConversationListItem[], ConversationListRequest>({
      query: ({ channel, limit = CONVERSATION_PAGE_SIZE, offset, status }) => ({
        url: '/chat/conversations',
        params: {
          limit,
          offset,
          ...(status ? { conversation_status: status } : {}),
          ...(channel ? { channel } : {}),
        },
      }),
      transformResponse: (response: ConversationListItemResponse[]) => response.map(mapListItem),
      providesTags: (result) => [
        { type: 'Conversation', id: 'LIST' },
        ...(result ?? []).map((conversation) => ({
          type: 'Conversation' as const,
          id: conversation.id,
        })),
      ],
    }),
    getConversation: build.query<ConversationDetail, string>({
      query: (conversationId) => `/chat/conversations/${conversationId}`,
      transformResponse: (response: ConversationDetailResponse) => mapDetail(response),
      providesTags: (_result, _error, conversationId) => [
        { type: 'Conversation', id: conversationId },
      ],
    }),
    closeConversation: build.mutation<void, string>({
      query: (conversationId) => ({
        url: `/chat/sessions/${conversationId}`,
        method: 'DELETE',
      }),
      transformResponse: () => undefined,
      invalidatesTags: (_result, _error, conversationId) => [
        { type: 'Conversation', id: conversationId },
        { type: 'Conversation', id: 'LIST' },
      ],
    }),
  }),
});

export const {
  useCloseConversationMutation,
  useGetConversationQuery,
  useLazyListConversationsQuery,
  useListConversationsQuery,
} = conversationsApi;

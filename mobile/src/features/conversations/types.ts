export const CONVERSATION_CHANNELS = ['WIDGET', 'CUSTOM_API'] as const;
export const CONVERSATION_STATUSES = ['OPEN', 'CLOSED'] as const;

export type ConversationChannel = (typeof CONVERSATION_CHANNELS)[number];
export type ConversationStatus = (typeof CONVERSATION_STATUSES)[number];

export type ConversationCustomer = {
  name: string | null;
  email: string | null;
  externalId: string | null;
  metadata: Record<string, unknown>;
};

export type ConversationCitation = {
  id: string;
  documentId: string;
  sourceName: string;
  pageNumber: number | null;
  chunkIndex: number;
  score: number;
  snippet: string;
  unitId: string | null;
  modality: string | null;
  sectionPath: string[];
  blockType: string | null;
  sheetName: string | null;
  cellRange: string | null;
  tableId: string | null;
};

export type ConversationMessage = {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  citations: ConversationCitation[];
  sequenceNumber: number | null;
  action: Record<string, unknown> | null;
};

export type ConversationListItem = {
  id: string;
  channel: ConversationChannel;
  customer: Omit<ConversationCustomer, 'metadata'>;
  status: ConversationStatus;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
};

export type ConversationDetail = Omit<ConversationListItem, 'messageCount' | 'customer'> & {
  customer: ConversationCustomer;
  messages: ConversationMessage[];
};

export type ConversationFilters = {
  status?: ConversationStatus;
  channel?: ConversationChannel;
};

export type ConversationListRequest = ConversationFilters & {
  offset: number;
  limit?: number;
};

export type ConversationMetadataEntry = {
  key: string;
  value: string;
};

export type TicketDraft = {
  title: string;
  description: string;
};

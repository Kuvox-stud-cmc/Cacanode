export type TenantWorkspace = {
  tenantId: string;
  knowledgeBase: {
    id: string;
    name: string;
    slug: string;
    defaultLocale: string;
  };
  chatbot: {
    id: string;
    displayName: string;
    defaultLocale: string;
    welcomeMessage: string;
  };
};

export type ChatSession = {
  id: string;
  chatbotId: string;
  knowledgeBaseId: string;
  tenantId: string;
  locale: string;
};

export type ChatCitation = {
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

export type ChatHistoryMessage = {
  role: 'user' | 'assistant' | 'system';
  content: string;
  citations: ChatCitation[];
  sequenceNumber: number | null;
  action: Record<string, unknown> | null;
};

export type AssistantResponse = {
  role: 'assistant';
  content: string;
  citations: ChatCitation[];
  action: Record<string, unknown> | null;
};

export type PlaygroundSession = {
  id: string;
  title: string;
  messageCount: number;
  status: string;
  createdAt: string;
  lastActivityAt: string;
};

export type TranscriptMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  citations: ChatCitation[];
  status: 'sent' | 'pending' | 'failed';
  failureMessage?: string;
  noInformation?: boolean;
};

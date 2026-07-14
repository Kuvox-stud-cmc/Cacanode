import {
  CONVERSATION_CHANNELS,
  CONVERSATION_STATUSES,
  type ConversationChannel,
  type ConversationFilters,
  type ConversationListItem,
  type ConversationMessage,
  type ConversationMetadataEntry,
  type ConversationStatus,
  type TicketDraft,
} from '@/features/conversations/types';

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function allowed<T extends string>(value: string | undefined, values: readonly T[]) {
  return value && values.includes(value as T) ? value as T : undefined;
}

export function conversationFiltersFromRoute(
  params: Record<string, string | string[] | undefined>,
): ConversationFilters {
  const status = allowed(first(params.status), CONVERSATION_STATUSES);
  const channel = allowed(first(params.channel), CONVERSATION_CHANNELS);
  return {
    ...(status ? { status: status as ConversationStatus } : {}),
    ...(channel ? { channel: channel as ConversationChannel } : {}),
  };
}

export function conversationFiltersToRoute(filters: ConversationFilters) {
  return {
    status: filters.status || undefined,
    channel: filters.channel || undefined,
  };
}

export function mergeConversationPages(pages: ConversationListItem[][]) {
  const seen = new Set<string>();
  return pages.flatMap((page) => page.filter((conversation) => {
    if (seen.has(conversation.id)) return false;
    seen.add(conversation.id);
    return true;
  }));
}

export function customerIdentity(customer: {
  name: string | null;
  email: string | null;
  externalId: string | null;
}) {
  return customer.name?.trim()
    || customer.email?.trim()
    || customer.externalId?.trim()
    || 'Anonymous customer';
}

export function displayMetadata(metadata: Record<string, unknown>): ConversationMetadataEntry[] {
  const entries: ConversationMetadataEntry[] = [];
  for (const [key, rawValue] of Object.entries(metadata)) {
    if (entries.length === 20) break;
    if (!['string', 'number', 'boolean'].includes(typeof rawValue)) continue;
    const value = String(rawValue).slice(0, 200);
    entries.push({ key, value });
  }
  return entries;
}

export function ticketDraftFromAction(action: Record<string, unknown> | null): TicketDraft | null {
  if (!action || action.type !== 'ticket_draft') return null;
  if (typeof action.title !== 'string' || typeof action.description !== 'string') return null;
  const title = action.title.trim();
  const description = action.description.trim();
  if (!title || !description) return null;
  return {
    title: title.slice(0, 200),
    description: description.slice(0, 2000),
  };
}

export function chronologicalMessages(messages: ConversationMessage[]) {
  return messages
    .map((message, index) => ({ message, index }))
    .sort((left, right) => {
      const leftSequence = left.message.sequenceNumber ?? Number.MAX_SAFE_INTEGER;
      const rightSequence = right.message.sequenceNumber ?? Number.MAX_SAFE_INTEGER;
      return leftSequence - rightSequence || left.index - right.index;
    })
    .map(({ message }) => message);
}

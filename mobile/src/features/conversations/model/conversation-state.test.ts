import {
  chronologicalMessages,
  conversationFiltersFromRoute,
  conversationFiltersToRoute,
  customerIdentity,
  displayMetadata,
  mergeConversationPages,
  ticketDraftFromAction,
} from '@/features/conversations/model/conversation-state';
import type { ConversationListItem, ConversationMessage } from '@/features/conversations/types';

describe('conversation list state', () => {
  it('restores only valid route filters and serializes them for detail navigation', () => {
    expect(conversationFiltersFromRoute({ status: 'CLOSED', channel: 'CUSTOM_API' }))
      .toEqual({ status: 'CLOSED', channel: 'CUSTOM_API' });
    expect(conversationFiltersFromRoute({ status: 'PENDING', channel: 'EMAIL' })).toEqual({});
    expect(conversationFiltersToRoute({ status: 'OPEN' })).toEqual({
      status: 'OPEN', channel: undefined,
    });
  });

  it('merges incremental pages without duplicate IDs', () => {
    const item = (id: string) => ({ id } as ConversationListItem);
    expect(mergeConversationPages([[item('1'), item('2')], [item('2'), item('3')]])
      .map(({ id }) => id)).toEqual(['1', '2', '3']);
  });
});

describe('conversation display safety', () => {
  it('uses name, email, external ID, then anonymous customer identity', () => {
    expect(customerIdentity({ name: ' Ada ', email: 'ada@example.com', externalId: 'ext' })).toBe('Ada');
    expect(customerIdentity({ name: null, email: 'ada@example.com', externalId: 'ext' })).toBe('ada@example.com');
    expect(customerIdentity({ name: null, email: null, externalId: 'ext' })).toBe('ext');
    expect(customerIdentity({ name: ' ', email: '', externalId: null })).toBe('Anonymous customer');
  });

  it('shows at most 20 top-level primitives, truncates values, and omits nested data', () => {
    const metadata: Record<string, unknown> = Object.fromEntries(
      Array.from({ length: 25 }, (_, index) => [`key-${index}`, index]),
    );
    metadata.long = 'x'.repeat(250);
    const mixed: Record<string, unknown> = { nested: { secret: true }, list: ['hidden'], ...metadata };
    const displayed = displayMetadata(mixed);
    expect(displayed).toHaveLength(20);
    expect(displayed.some(({ key }) => key === 'nested' || key === 'list')).toBe(false);
    expect(displayMetadata({ long: 'x'.repeat(250) })[0].value).toHaveLength(200);
  });

  it('accepts valid ticket drafts and ignores malformed or unknown actions', () => {
    expect(ticketDraftFromAction({
      type: 'ticket_draft', title: ' Refund ', description: ' Review the order. ',
    })).toEqual({ title: 'Refund', description: 'Review the order.' });
    expect(ticketDraftFromAction({ type: 'ticket_created', title: 'No', description: 'No' })).toBeNull();
    expect(ticketDraftFromAction({ type: 'ticket_draft', title: 123, description: 'No' })).toBeNull();
    expect(ticketDraftFromAction({ type: 'ticket_draft', title: '', description: 'No' })).toBeNull();
  });

  it('renders messages chronologically while keeping missing sequences stable', () => {
    const message = (id: string, sequenceNumber: number | null) => ({
      id, sequenceNumber,
    } as ConversationMessage);
    expect(chronologicalMessages([
      message('third', 3), message('unknown-1', null), message('first', 1), message('unknown-2', null),
    ]).map(({ id }) => id)).toEqual(['first', 'third', 'unknown-1', 'unknown-2']);
  });
});

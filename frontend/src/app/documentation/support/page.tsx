import { BulletList, Callout, DocArticle, DocSection, FeatureBadge, ParameterTable, Step, Steps } from "@/components/documentation/DocumentationComponents"

export default function SupportDocumentationPage() {
  return <DocArticle title="Conversations and tickets" description="Follow customer chat activity across channels and turn conversations into owned, trackable support work.">
    <DocSection id="conversations" title="Customer conversations">
      <p>The Conversations area contains customer sessions, not employee playground history. Open a conversation to review customer identity, metadata, message count, status, timestamps, and the complete ordered message transcript.</p>
    </DocSection>
    <DocSection id="channels" title="Channels and closing">
      <ParameterTable parameters={[{ name: "WIDGET", type: "channel", description: "Conversation created by the hosted website widget." }, { name: "CUSTOM_API", type: "channel", description: "Conversation created through /api/v1/external/chat/sessions." }, { name: "OPEN", type: "status", description: "The customer session can still receive messages." }, { name: "CLOSED", type: "status", description: "The session was closed by a user, API caller, or idle timeout." }]} />
      <p>Closing a conversation is final for that session and emits a <code>conversation.closed</code> webhook when webhooks are enabled. Start a new session if the customer returns with another request.</p>
    </DocSection>
    <DocSection id="tickets" title="Ticket fields">
      <BulletList><li><strong>Status:</strong> <code>OPEN</code>, <code>IN_PROGRESS</code>, <code>RESOLVED</code>, or <code>CLOSED</code>.</li><li><strong>Priority:</strong> <code>LOW</code>, <code>NORMAL</code>, <code>HIGH</code>, or <code>URGENT</code>.</li><li><strong>Source:</strong> <code>WIDGET</code> or <code>CUSTOM_API</code>.</li><li><strong>Assignee:</strong> an active workspace team member, or unassigned.</li><li><strong>Notes:</strong> internal updates with author and creation time.</li></BulletList>
    </DocSection>
    <DocSection id="workflow" title="Ticket lifecycle">
      <Steps><Step title="Open">A customer or integration creates a ticket. New tickets default to an open support state.</Step><Step title="Triage">Set priority and assign an owner. Add internal notes with relevant investigation context.</Step><Step title="Work">Move the ticket to <code>IN_PROGRESS</code> while the request is actively handled.</Step><Step title="Resolve and close">Use <code>RESOLVED</code> when the issue has an answer, then <code>CLOSED</code> when no more support work is expected.</Step></Steps>
      <Callout type="note">Ticket status does not automatically close its source conversation, and closing a conversation does not automatically resolve its tickets. Manage both lifecycles deliberately.</Callout>
    </DocSection>
    <DocSection id="filters" title="Filters, assignees, and notes">
      <p>Search ticket title, description, and customer details, then narrow the list by status, priority, source, assigned user, unassigned state, and created date. Sort direction and pagination are preserved in the list controls.</p>
      <Callout type="security"><FeatureBadge>Internal</FeatureBadge> Ticket notes are workspace-only operational context. Do not use notes as a customer reply channel.</Callout>
    </DocSection>
  </DocArticle>
}

import { BulletList, Callout, CodeBlock, DocArticle, DocSection, FeatureBadge, ParameterTable } from "@/components/documentation/DocumentationComponents"

const envelope = `{
  "id": "EVENT_ID",
  "type": "conversation.started",
  "createdAt": "2026-07-20T10:00:00",
  "data": {
    "conversationId": "CONVERSATION_ID",
    "chatbotId": "CHATBOT_ID",
    "channel": "CUSTOM_API",
    "externalUserId": "user_123"
  }
}`

const verify = `import { createHmac, timingSafeEqual } from "node:crypto";

export function verifyCacaNodeWebhook(rawBody, headers) {
  const timestamp = headers["x-cacanode-timestamp"];
  const supplied = headers["x-cacanode-signature"];
  if (!timestamp || !supplied?.startsWith("v1=")) return false;

  const expectedHex = createHmac("sha256", process.env.CACANODE_WEBHOOK_SECRET)
    .update(timestamp + "." + rawBody)
    .digest("hex");
  const expected = Buffer.from(expectedHex, "hex");
  const actual = Buffer.from(supplied.slice(3), "hex");

  return expected.length === actual.length && timingSafeEqual(expected, actual);
}`

export default function WebhooksDocumentationPage() {
  return <DocArticle title="Webhooks" description="Receive signed, retried HTTP notifications when customer conversations start or close and when support tickets are created.">
    <DocSection id="requirements" title="Requirements and events">
      <p><FeatureBadge kind="pro">Pro</FeatureBadge> <FeatureBadge kind="admin">Tenant administrator</FeatureBadge> Webhook creation, testing, delivery, and secret rotation require Pro and administrator access. Saved endpoints are preserved when Pro lapses, but delivery is disabled.</p>
      <ParameterTable parameters={[{ name: "conversation.started", type: "event", description: "A Widget or Custom API customer session was created." }, { name: "conversation.closed", type: "event", description: "A customer session was closed explicitly or by idle timeout." }, { name: "ticket.created", type: "event", description: "A support ticket was created from a customer session." }]} />
      <Callout type="security">Production destinations must use HTTPS and cannot resolve to private, loopback, link-local, or site-local network addresses. Redirects are not followed.</Callout>
    </DocSection>
    <DocSection id="payload" title="Payload envelope">
      <CodeBlock language="json" code={envelope} />
      <BulletList><li><code>id</code> is the event identifier and is also sent as <code>X-Cacanode-Event-Id</code>.</li><li><code>type</code> is the subscribed event name.</li><li><code>createdAt</code> is the event creation timestamp.</li><li><code>data</code> contains event-specific camelCase fields.</li></BulletList>
      <p><code>ticket.created</code> data contains <code>ticketId</code>, <code>conversationId</code>, <code>chatbotId</code>, <code>customerEmail</code>, <code>title</code>, and <code>status</code>. Conversation events contain <code>conversationId</code>, <code>chatbotId</code>, <code>channel</code>, and <code>externalUserId</code>; idle closures also include <code>{'reason: "idle_timeout"'}</code>.</p>
    </DocSection>
    <DocSection id="verify" title="Verify signatures">
      <p>CacaNode computes <code>{'HMAC-SHA256(secret, timestamp + "." + rawBody)'}</code> and sends the lowercase hexadecimal digest as <code>X-Cacanode-Signature: v1=&lt;hex&gt;</code>. The Unix-seconds timestamp is in <code>X-Cacanode-Timestamp</code>.</p>
      <CodeBlock language="javascript" code={verify} />
      <Callout type="warning">Verify against the exact raw request body bytes before JSON parsing. Re-serializing JSON can change whitespace or key order and invalidate the signature. Also reject timestamps outside your replay-tolerance window.</Callout>
    </DocSection>
    <DocSection id="delivery" title="Delivery and retries">
      <p>Your endpoint must return any <strong>2xx</strong> status within the delivery timeout. All other responses, timeouts, DNS failures, and connection failures count as unsuccessful.</p>
      <BulletList><li>CacaNode attempts an event up to five times.</li><li>Failed attempts are retried with increasing delays (approximately 1, 5, 25, then 125 minutes).</li><li>Each subscribed endpoint is attempted; the event completes only after all selected deliveries succeed or the attempt limit is reached.</li><li>Make processing idempotent by storing the event ID before applying side effects.</li></BulletList>
    </DocSection>
    <DocSection id="testing-rotation" title="Testing and secret rotation">
      <p>Use <strong>Send test</strong> to queue a signed <code>test</code> payload for one endpoint. The signing secret is shown only when an endpoint is created or its secret is rotated.</p>
      <Callout type="security">For rotation without downtime, deploy code that can temporarily validate both the current and new secret, rotate in CacaNode, update your secret store, confirm a test delivery, and then remove the old value.</Callout>
    </DocSection>
  </DocArticle>
}

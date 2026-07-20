import { BulletList, Callout, DocArticle, DocSection, FeatureBadge, ParameterTable } from "@/components/documentation/DocumentationComponents"

export default function WorkspaceDocumentationPage() {
  return <DocArticle title="Workspace settings" description="Control customer answer behavior, usage limits, subscription state, and integration credentials from one administrator-only area.">
    <Callout type="note"><FeatureBadge kind="admin">Tenant administrator</FeatureBadge> The settings described on this page are restricted to tenant administrators.</Callout>
    <DocSection id="instructions" title="Customer answer instructions">
      <p>Answer instructions guide responses in the Website Widget and Custom Chat API. Use them to define tone, audience, escalation rules, and constraints that should apply across customer conversations.</p>
      <BulletList><li>Instructions must not be blank and are limited to 4,000 characters.</li><li>Changes apply to subsequent customer answers.</li><li>The restore action replaces the custom text with the platform default.</li><li>Instructions do not grant access to employee-only documents or override citation visibility.</li></BulletList>
      <Callout type="tip">Write operational instructions, not source facts. Keep changing policies in documents so answers can cite them and content owners can update them independently.</Callout>
    </DocSection>
    <DocSection id="quotas" title="Quotas and usage">
      <ParameterTable parameters={[{ name: "Messages", type: "period usage", description: "Shared by chat surfaces and reset on the billing anniversary or plan quota window." }, { name: "Documents", type: "live total", description: "Current stored document count." }, { name: "Team members", type: "live total", description: "Active members subject to the plan limit." }, { name: "Storage", type: "live total", description: "Stored document size measured in MB." }]} />
      <p>When a live total is over limit, existing data is preserved but new growth is blocked until usage is reduced or the plan is renewed/upgraded. Message quota exhaustion blocks new chat messages.</p>
    </DocSection>
    <DocSection id="billing" title="Billing lifecycle">
      <BulletList><li>New registrations begin with a time-limited Pro trial; trial expiration falls directly to Starter.</li><li>Paid Pro can be monthly or annual. Annual plans retain monthly message windows anchored to the paid activation date.</li><li>Renewals are manual hosted checkouts. Early renewal extends from <code>paidThroughAt</code> and does not reset the current quota window early.</li><li>Paid expiration enters a three-day grace period. Grace keeps the final Pro quota window and does not grant another allowance.</li><li>After grace, the workspace falls back to Starter. Existing documents, users, webhook configuration, and branding preferences remain stored.</li><li>A Starter change during paid Pro is scheduled after prepaid access and grace; during trial it ends the trial immediately.</li></BulletList>
      <Callout type="security">A checkout return URL is not proof of payment. CacaNode activates entitlements only after verified payment processing and exposes the current state in Billing settings.</Callout>
    </DocSection>
    <DocSection id="features" title="Plan-gated features">
      <div className="grid gap-3 sm:grid-cols-2">{[["Custom Chat API", "api:chat token creation and use"], ["Webhooks", "endpoint creation, testing, delivery, and rotation"], ["Advanced analytics", "detailed scoped trends and popular questions"], ["Custom branding", "hide CacaNode branding in the widget"]].map(([name, detail]) => <div key={name} className="rounded-lg border p-4"><FeatureBadge kind="pro">Pro</FeatureBadge><h3 className="mt-2 font-semibold text-slate-950">{name}</h3><p className="text-sm text-slate-600">{detail}</p></div>)}</div>
      <p>The hosted widget and dashboard summary remain available on Starter within the active plan limits. Always use the live plan catalog and Billing feature badges as the source of current commercial limits.</p>
    </DocSection>
    <DocSection id="tokens" title="Integration tokens">
      <p>Create separate tokens for separate environments and purposes. Available scopes are <code>widget:chat</code> and <code>api:chat</code>; the secret is shown only at creation or rotation.</p>
      <BulletList><li>Name tokens by surface and environment.</li><li>Use optional expiration dates for temporary integrations.</li><li>Rotate a normal token to replace its secret, or revoke it to stop access.</li><li>The automatic website token is managed from Widget settings and cannot be rotated like a regular token.</li><li>Deleted tokens can be included in the administrator list for audit context.</li></BulletList>
    </DocSection>
    <DocSection id="admin" title="Administrator-only settings">
      <p>Only tenant administrators can manage plans and billing, integration tokens, widget configuration and icons, webhooks, customer answer instructions, team invitations and roles, and customer-visible document access. Regular users can still use the main workspace features allowed by the current plan.</p>
    </DocSection>
  </DocArticle>
}

import { BulletList, Callout, DocArticle, DocSection, FeatureBadge, ParameterTable, Step, Steps } from "@/components/documentation/DocumentationComponents"

export default function AnalyticsTeamDocumentationPage() {
  return <DocArticle title="Analytics and team" description="Monitor workspace activity, compare support performance, and manage access without losing administrator coverage.">
    <DocSection id="dashboard" title="Dashboard metrics">
      <p>The standard dashboard summarizes total documents, documents added this week, user messages this month compared with the previous month, stored document bytes against the storage limit, active users, and recent uploads.</p>
      <Callout type="note">Dashboard totals are workspace-level operational summaries. They are available independently of the Pro advanced analytics feature.</Callout>
    </DocSection>
    <DocSection id="analytics" title="Pro analytics">
      <p><FeatureBadge kind="pro">Pro</FeatureBadge> Advanced analytics adds conversations, average assistant response time, closed-session rate, user messages, daily message volume, popular questions, and—within the customer scope—resolved ticket rate.</p>
      <p>Each headline metric includes the previous comparable period or percentage-point change. A higher response-time change means responses became slower, while a higher closed or resolved rate generally indicates more completed support work.</p>
    </DocSection>
    <DocSection id="date-ranges" title="Scopes and date ranges">
      <ParameterTable parameters={[{ name: "CUSTOMER", type: "scope", description: "Widget and Custom API activity, including resolved ticket rate." }, { name: "EMPLOYEE", type: "scope", description: "Authenticated employee playground activity." }, { name: "ALL", type: "scope", description: "Combined customer and employee chat activity." }, { name: "7 / 30 / 90", type: "days", description: "Selectable rolling analytics windows." }]} />
    </DocSection>
    <DocSection id="roles" title="Team roles">
      <div className="grid gap-4 sm:grid-cols-2"><div className="rounded-lg border p-4"><FeatureBadge>USER</FeatureBadge><p className="mt-3 text-sm leading-6">Uses core workspace features such as documents, employee chat, dashboard, conversations, tickets, and analytics when entitled.</p></div><div className="rounded-lg border p-4"><FeatureBadge kind="admin">TENANT_ADMIN</FeatureBadge><p className="mt-3 text-sm leading-6">Also manages users, invitations, customer-visible documents, answer instructions, widget settings, tokens, webhooks, plans, and billing.</p></div></div>
    </DocSection>
    <DocSection id="invitations" title="Invitations and deactivation">
      <Steps><Step title="Invite">Choose an email and either <code>USER</code> or <code>TENANT_ADMIN</code>. Team-member quotas are enforced when inviting.</Step><Step title="Follow up">Pending or expired invitations can be resent; pending invitations can be cancelled.</Step><Step title="Manage access">Administrators can change another member’s role or set the account to <code>INACTIVE</code>.</Step><Step title="Reactivate">Restoring an inactive member is subject to the current team-member quota.</Step></Steps>
      <Callout type="security">Deactivation revokes that user’s refresh tokens. Use it when a teammate leaves or access should stop immediately without deleting historical ownership data.</Callout>
    </DocSection>
    <DocSection id="protections" title="Administrator protections">
      <BulletList><li>An administrator cannot change their own role.</li><li>An administrator cannot deactivate their own account.</li><li>The final active tenant administrator cannot be demoted or deactivated.</li><li>Promote a second trusted administrator before changing the only active administrator.</li></BulletList>
    </DocSection>
  </DocArticle>
}

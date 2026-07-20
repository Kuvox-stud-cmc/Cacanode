import { BulletList, Callout, CodeBlock, DocArticle, DocSection, FeatureBadge, RelatedLinks, Step, Steps } from "@/components/documentation/DocumentationComponents"

const embed = `<script
  async
  src="WIDGET_SCRIPT_URL"
  data-token="CACANODE_WIDGET_TOKEN"
></script>`

export default function WidgetDocumentationPage() {
  return <DocArticle title="Website widget" description="Configure and install CacaNode’s hosted customer chat experience without building a chat interface yourself.">
    <Callout type="note"><FeatureBadge kind="admin">Tenant administrator</FeatureBadge> Widget configuration, managed tokens, branding, icons, and origins are administrator-only settings.</Callout>
    <DocSection id="configure" title="Configure the widget">
      <p>Open <strong>Settings → Widget</strong> to set the display name, welcome message, six-digit hex primary color, bottom-left or bottom-right position, and active state.</p>
      <BulletList><li>Choose the built-in icon or upload a custom icon.</li><li>Select <strong>Standard</strong>, <strong>Glow</strong>, <strong>Pulse</strong>, or <strong>Soft shadow</strong> launcher styling.</li><li><FeatureBadge kind="pro">Pro</FeatureBadge> Custom branding can hide CacaNode attribution. Starter workspaces continue to show it.</li></BulletList>
    </DocSection>
    <DocSection id="origins" title="Restrict allowed origins">
      <p>Add exact HTTP or HTTPS origins such as <code>https://www.example.com</code>. Do not include a path. When the list is non-empty, requests must come from one of the configured parent origins.</p>
      <Callout type="security">List every production and staging origin that hosts the widget. An empty list does not provide an origin allowlist; use explicit origins for production deployments.</Callout>
    </DocSection>
    <DocSection id="token" title="Generate the managed token">
      <Steps><Step title="Open Widget settings">Find the managed embed section.</Step><Step title="Generate the embed token">CacaNode creates a token scoped to <code>widget:chat</code>. Its secret is displayed once.</Step><Step title="Save it securely">Store the value as <code>CACANODE_WIDGET_TOKEN</code> in your deployment environment or secret manager.</Step></Steps>
      <Callout type="warning">Rotating the managed token invalidates the previous widget credential. Update the deployed embed value at the same time.</Callout>
    </DocSection>
    <DocSection id="embed" title="Add the embed script">
      <p>Use the exact script URL shown in Widget settings and inject the environment value during deployment.</p>
      <CodeBlock language="html" code={embed} />
      <p>Place the script near the end of <code>&lt;body&gt;</code> or in the site-wide layout. The <code>async</code> attribute keeps widget loading off the critical rendering path.</p>
    </DocSection>
    <DocSection id="security" title="Security and evidence links">
      <BulletList><li>A browser widget token is visible to visitors in page source and network requests. Its limited <code>widget:chat</code> scope and origin checks are the security boundary.</li><li>Never reuse an <code>api:chat</code> token in browser code.</li><li>Keep the widget inactive until configuration and source visibility have been tested.</li><li>Revoke or rotate tokens after suspected exposure.</li></BulletList>
      <Callout type="security" title="Temporary public evidence">Customer citations can contain a signed <code>public_url</code>. Links expire (the current default is one hour) and only open a document while it remains completed, customer-visible, and associated with a valid integration. They do not expose employee-only or incomplete sources.</Callout>
      <RelatedLinks links={[{ href: "/documentation/documents#visibility", title: "Set customer visibility", description: "Control which sources customer channels can retrieve." }, { href: "/documentation/support", title: "Manage widget conversations", description: "Review conversations and tickets created by visitors." }]} />
    </DocSection>
  </DocArticle>
}

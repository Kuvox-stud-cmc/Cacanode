import Link from "next/link"
import { Callout, DocArticle, DocSection, RelatedLinks, Step, Steps } from "@/components/documentation/DocumentationComponents"

export default function GettingStartedPage() {
  return <DocArticle title="Getting started" description="Go from a source file to a cited answer, then choose the integration that fits your audience.">
    <Steps>
      <Step title="Sign in to your workspace">Open the dashboard with an active workspace account. Tenant administrators and regular users can upload employee-only sources; only administrators can make sources customer-visible.</Step>
      <Step title="Upload a useful source">Start with a text-based policy, product guide, FAQ, or spreadsheet smaller than 20 MB.</Step>
      <Step title="Wait for indexing">Do not test the source until its status is <strong>Completed</strong>.</Step>
      <Step title="Ask a specific question">Use the playground and open the answer citations to verify the source.</Step>
    </Steps>
    <DocSection id="upload" title="1. Upload a document">
      <p>Open <Link className="text-indigo-700 underline" href="/documents">Documents</Link>, choose <strong>Upload document</strong>, select the visibility, and submit the file. Clear, well-structured source material produces the easiest citations to review.</p>
      <Callout type="warning">Scanned PDFs and legacy Office files are not supported. Export image-only scans with OCR and save old <code>.doc</code> or <code>.xls</code> files in a current format before upload.</Callout>
    </DocSection>
    <DocSection id="index" title="2. Wait for indexing">
      <p>New uploads move through <strong>Pending</strong> and <strong>Processing</strong>. A <strong>Completed</strong> file is available for retrieval. A <strong>Failed</strong> file includes an error message; correct the source and upload it again.</p>
    </DocSection>
    <DocSection id="test" title="3. Test the playground">
      <p>Open the Chat playground, ask a question answered by the document, and review the source cards beneath the response. Open a citation to see the relevant page, sheet, cell range, section, or text unit in the document viewer.</p>
      <Callout type="tip">Test both a question the source can answer and one it cannot. This checks that your answer instructions and evidence boundary behave as expected.</Callout>
    </DocSection>
    <DocSection id="next-step" title="4. Choose what to build next">
      <RelatedLinks links={[{ href: "/documentation/widget", title: "Add website chat", description: "Use the hosted widget for customer-facing answers." }, { href: "/documentation/api", title: "Build a custom integration", description: "Use the Pro API for a server-owned chat experience." }, { href: "/documentation/workspace", title: "Tune answer behavior", description: "Set customer answer instructions and review quotas." }]} />
    </DocSection>
  </DocArticle>
}

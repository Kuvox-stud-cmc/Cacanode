import { BulletList, Callout, DocArticle, DocSection, FeatureBadge, ParameterTable } from "@/components/documentation/DocumentationComponents"

export default function DocumentsDocumentationPage() {
  return <DocArticle title="Documents" description="Upload and manage the source material CacaNode uses for retrieval, answers, and citations.">
    <DocSection id="supported-files" title="Supported files and limits">
      <ParameterTable parameters={[
        { name: "PDF", type: ".pdf", description: "Text-based PDF documents; scanned image-only PDFs are excluded." },
        { name: "Word", type: ".docx", description: "Current Word format; legacy .doc files are excluded." },
        { name: "Text", type: ".txt, .md, .markdown", description: "Plain text and Markdown." },
        { name: "HTML", type: ".html, .htm", description: "HTML documents." },
        { name: "Spreadsheet", type: ".xlsx, .csv", description: "Current Excel workbooks and CSV data; legacy .xls files are excluded." },
      ]} />
      <Callout type="warning" title="20 MB per file">Files larger than 20 MB are rejected. Split large sources into coherent documents instead of arbitrary fragments so citations stay understandable.</Callout>
    </DocSection>
    <DocSection id="indexing" title="Indexing statuses">
      <BulletList><li><strong>Pending:</strong> the upload was accepted and is waiting for ingestion.</li><li><strong>Processing:</strong> text and structure are being extracted and indexed.</li><li><strong>Completed:</strong> the source can be retrieved and cited.</li><li><strong>Failed:</strong> ingestion stopped; review the error and upload a corrected file.</li></BulletList>
    </DocSection>
    <DocSection id="visibility" title="Document visibility">
      <div className="grid gap-4 sm:grid-cols-2"><div className="rounded-lg border p-4"><FeatureBadge>EMPLOYEE_ONLY</FeatureBadge><p className="mt-3 text-sm leading-6">Available to authenticated workspace users in the employee playground. It is never exposed in Widget or Custom API evidence.</p></div><div className="rounded-lg border p-4"><FeatureBadge kind="admin">CUSTOMER_AND_EMPLOYEE</FeatureBadge><p className="mt-3 text-sm leading-6">Available in employee and customer channels. Only a tenant administrator can assign customer-visible access.</p></div></div>
      <Callout type="security">Visibility is an evidence boundary, not just a display filter. Customer answers and signed evidence URLs exclude employee-only sources.</Callout>
    </DocSection>
    <DocSection id="viewer-citations" title="Viewer and citations">
      <p>Select a completed document to open its viewer. Citation links can focus a precise indexed unit or chunk. Depending on the source, metadata can include page number, section path, heading, sheet name, cell range, table identifier, or text offsets.</p>
      <p>Use citation snippets and source locations to verify the answer against the original material. A citation score is retrieval relevance, not a guarantee that the source statement is correct.</p>
    </DocSection>
    <DocSection id="manage" title="Search, filter, and delete">
      <p>The document list supports text search plus status, file type, and access filters. Deletion removes the document from the workspace and retrieval pipeline. Existing chat text may remain in conversation history, but deleted evidence can no longer be opened or retrieved.</p>
      <Callout type="warning">Deletion is intended to be permanent. Confirm that no employee or customer workflow still depends on the source before removing it.</Callout>
    </DocSection>
  </DocArticle>
}

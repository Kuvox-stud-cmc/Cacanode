import { BulletList, Callout, DocArticle, DocSection } from "@/components/documentation/DocumentationComponents"

export default function PlaygroundDocumentationPage() {
  return <DocArticle title="Chat playground" description="Ask private workspace questions, validate answer quality, and inspect the evidence behind each response.">
    <DocSection id="ask" title="Ask questions">
      <p>The playground is an authenticated employee channel. It can retrieve both <code>EMPLOYEE_ONLY</code> and <code>CUSTOMER_AND_EMPLOYEE</code> documents. Start a new conversation for a new subject so prior messages do not influence the context.</p>
      <Callout type="tip">Ask the same question your customer or teammate would use. Natural phrasing is a better retrieval test than copying a document heading verbatim.</Callout>
    </DocSection>
    <DocSection id="sources" title="Review sources and citations">
      <p>Assistant responses can include source citations with snippets and structured locations. Select a citation to open the document viewer focused on the relevant page, section, spreadsheet cells, or indexed unit.</p>
      <Callout type="note">No citation usually means the model did not return grounded evidence for that response. Rephrase the question, check indexing, and verify the source contains the answer.</Callout>
    </DocSection>
    <DocSection id="history" title="Search conversation history">
      <p>The history panel works like a ChatGPT-style conversation list. Search by conversation title or message content, then reopen a result without losing its prior turns and citations.</p>
      <BulletList><li>Recent conversations are ordered by activity.</li><li>The first user message supplies a short default title.</li><li>History belongs to the signed-in playground user, not to website visitors.</li></BulletList>
    </DocSection>
    <DocSection id="manage" title="Manage conversations">
      <p>Create a conversation when switching topics. Hide threads that are no longer useful to remove them from your history; they may still be retained for workspace analytics. Customer conversations from the Widget and Custom API are managed separately under Conversations.</p>
    </DocSection>
  </DocArticle>
}

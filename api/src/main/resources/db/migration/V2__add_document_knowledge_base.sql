ALTER TABLE documents
    ADD COLUMN knowledge_base_id UUID;

UPDATE documents d
SET knowledge_base_id = kb.id
FROM knowledge_bases kb
WHERE kb.tenant_id = d.tenant_id
  AND d.knowledge_base_id IS NULL;

ALTER TABLE documents
    ALTER COLUMN knowledge_base_id SET NOT NULL,
    ADD CONSTRAINT fk_documents_knowledge_base
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id)
        ON DELETE RESTRICT;

CREATE INDEX idx_document_knowledge_base_id ON documents(knowledge_base_id);
CREATE INDEX idx_document_tenant_status ON documents(tenant_id, status);
CREATE INDEX idx_document_tenant_knowledge_base ON documents(tenant_id, knowledge_base_id);

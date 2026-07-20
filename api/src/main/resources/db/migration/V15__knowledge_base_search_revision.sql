ALTER TABLE knowledge_bases
    ADD COLUMN search_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE knowledge_bases
    ADD CONSTRAINT chk_knowledge_base_search_revision_non_negative
        CHECK (search_revision >= 0);

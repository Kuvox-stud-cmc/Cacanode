package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseImmutableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_interview_template_revisions")
public class InterviewTemplateRevision extends BaseImmutableEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(name = "template_id", nullable = false, updatable = false) private UUID templateId;
    @Column(name = "revision_number", nullable = false, updatable = false) private int revisionNumber;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "content", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String content;
    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64) private String contentSha256;
}

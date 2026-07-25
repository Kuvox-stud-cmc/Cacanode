package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_interview_templates")
public class InterviewTemplate extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(nullable = false) private String locale;
    @Column(nullable = false) private boolean archived;
    @Column(name = "archived_at") private LocalDateTime archivedAt;
    @Version @Column(name = "version", nullable = false) private long version;
}

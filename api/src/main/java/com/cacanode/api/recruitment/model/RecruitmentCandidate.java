package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_candidates")
public class RecruitmentCandidate extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(name = "normalized_name", nullable = false) private String normalizedName;
    @Column(nullable = false) private String email;
    @Column(name = "normalized_email", nullable = false) private String normalizedEmail;
    private String phone;
    @Column(columnDefinition = "text") private String notes;
    @Version @Column(name = "version", nullable = false) private long version;
}

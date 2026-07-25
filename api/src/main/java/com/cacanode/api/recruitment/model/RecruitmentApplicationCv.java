package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_application_cvs")
public class RecruitmentApplicationCv extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "application_id") private UUID applicationId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "original_filename", nullable = false) private String originalFilename;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "byte_size", nullable = false) private long byteSize;
    @Column(name = "content_sha256", nullable = false, length = 64) private String contentSha256;
    @Enumerated(EnumType.STRING) @Column(name = "storage_state", nullable = false) private RecruitmentEnums.CvStorageState storageState;
    @Column(name = "quarantine_object_key") private String quarantineObjectKey;
    @Column(name = "promoted_object_key") private String promotedObjectKey;
    @Column(name = "storage_reservation_id", nullable = false) private UUID storageReservationId;
    @Column(nullable = false) private boolean active;
    @Column(name = "retained_until") private LocalDateTime retainedUntil;
    @Column(name = "deletion_attempts", nullable = false) private int deletionAttempts;
    @Column(name = "deletion_next_attempt_at") private LocalDateTime deletionNextAttemptAt;
    @Column(name = "deletion_last_error") private String deletionLastError;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Version @Column(name = "version", nullable = false) private long version;
}

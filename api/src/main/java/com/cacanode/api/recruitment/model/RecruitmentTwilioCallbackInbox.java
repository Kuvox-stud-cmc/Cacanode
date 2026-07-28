package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name="recruitment_twilio_callback_inbox")
public class RecruitmentTwilioCallbackInbox extends BaseEntity {
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="call_attempt_id",nullable=false,updatable=false) private UUID callAttemptId;
    @Column(name="twilio_call_sid",updatable=false,length=40) private String twilioCallSid;
    @Enumerated(EnumType.STRING) @Column(name="callback_kind",nullable=false,updatable=false)
    private RecruitmentEnums.TwilioCallbackKind callbackKind;
    @Column(name="sequence_number",updatable=false) private Long sequenceNumber;
    @Column(name="semantic_key",nullable=false,updatable=false,length=120) private String semanticKey;
    @Column(name="payload_sha256",nullable=false,updatable=false,length=64) private String payloadSha256;
    @Enumerated(EnumType.STRING) @Column(name="processing_result",nullable=false)
    private RecruitmentEnums.TwilioCallbackResult processingResult;
    @Column(name="processed_at",nullable=false) private LocalDateTime processedAt;
}

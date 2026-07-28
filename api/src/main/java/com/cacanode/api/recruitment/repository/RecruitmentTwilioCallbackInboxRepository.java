package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentEnums.TwilioCallbackKind;
import com.cacanode.api.recruitment.model.RecruitmentTwilioCallbackInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecruitmentTwilioCallbackInboxRepository extends JpaRepository<RecruitmentTwilioCallbackInbox,UUID> {
    Optional<RecruitmentTwilioCallbackInbox> findByCallAttemptIdAndCallbackKindAndSemanticKey(
            UUID callAttemptId,TwilioCallbackKind callbackKind,String semanticKey);
}

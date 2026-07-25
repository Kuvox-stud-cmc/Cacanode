package com.cacanode.api.notification.listener;

import com.cacanode.api.notification.service.EmailService;
import com.cacanode.api.recruitment.api.event.CandidateAccessEmailRequestedEvent;
import com.cacanode.api.recruitment.api.event.CandidatePrivacyDeletionConfirmationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class CandidateAccessEmailListener {
    private final EmailService emailService;

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void send(CandidateAccessEmailRequestedEvent event){
        emailService.sendRecruitmentCandidateAccessEmail(event.email(),event.fullName(),event.companyName(),
                event.jobTitle(),event.locale(),event.accessUrl(),event.verification());
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void sendDeletion(CandidatePrivacyDeletionConfirmationRequestedEvent event){
        emailService.sendRecruitmentPrivacyDeletionConfirmation(event.email(),event.fullName(),event.companyName(),
                event.jobTitle(),event.locale(),event.confirmationUrl());
    }
}

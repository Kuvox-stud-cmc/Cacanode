package com.cacanode.api.notification.listener;

import com.cacanode.api.notification.service.EmailService;
import com.cacanode.api.recruitment.api.event.CandidateAccessEmailRequestedEvent;
import com.cacanode.api.recruitment.api.event.CandidatePrivacyDeletionConfirmationRequestedEvent;
import com.cacanode.api.recruitment.api.event.CandidateCompletionEmailRequestedEvent;
import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class CandidateAccessEmailListener {
    private final EmailService emailService;
    private final ModuleEventInboxService inboxService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCompletion(CandidateCompletionEmailRequestedEvent event) {
        if (!inboxService.claim("notification.recruitment-candidate-completion-email")) return;
        emailService.sendRecruitmentCandidateCompletionEmail(
                event.email(), event.fullName(), event.companyName(), event.jobTitle(),
                event.locale(), event.completionUrl());
    }

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

package com.cacanode.api.notification.listener;

import com.cacanode.api.notification.service.EmailService;
import com.cacanode.api.recruitment.api.event.RecruitmentCandidateEmailDispatchEvent;
import com.cacanode.api.recruitment.api.RecruitmentEmailDeliveryCallbackApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class RecruitmentCandidateEmailDispatchListener {
    private final EmailService email;
    private final RecruitmentEmailDeliveryCallbackApi deliveries;

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void send(RecruitmentCandidateEmailDispatchEvent event){
        try {email.sendRecruitmentInterviewEmail(event.email(),event.candidateName(),event.companyName(),event.jobTitle(),
                event.locale(),event.managementUrl(),event.kind(),event.scheduledStartAt(),event.schedulingTimezone());
            deliveries.complete(event.deliveryId(),true,null);
        } catch(RuntimeException failure){deliveries.complete(event.deliveryId(),false,failure.getMessage());}
    }
}

package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.api.event.RecruitmentCandidateEmailDispatchEvent;
import com.cacanode.api.recruitment.api.RecruitmentEmailDeliveryCallbackApi;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.cacanode.api.recruitment.query.RecruitmentInvitationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class RecruitmentCandidateEmailDeliveryService implements RecruitmentEmailDeliveryCallbackApi {
    private final RecruitmentCandidateEmailDeliveryRepository deliveries;
    private final RecruitmentInterviewInvitationTokenRepository invitationTokens;
    private final RecruitmentInterviewRepository interviews;
    private final RecruitmentTokenSupport tokens;
    private final PublicRecruitmentProperties properties;
    private final RecruitmentInvitationQueryService queries;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Scheduled(fixedDelayString="${app.recruitment.email-delivery-ms:1000}")
    @Transactional
    public void dispatchDue(){
        LocalDateTime now=LocalDateTime.now(clock);
        for(var delivery:deliveries.lockDue(now))dispatch(delivery,now);
    }

    private void dispatch(RecruitmentCandidateEmailDelivery delivery,LocalDateTime now){
        RecruitmentInterview interview=interviews.findByIdAndTenantId(delivery.getInterviewId(),delivery.getTenantId()).orElse(null);
        if(interview==null || interview.getStatus()==InterviewStatus.CANCELLED || interview.getStatus()==InterviewStatus.EXPIRED){
            delivery.setState(CandidateEmailState.CANCELLED);delivery.setCancelledAt(now);deliveries.save(delivery);return;
        }
        var recipient=queries.recipient(delivery.getTenantId(),delivery.getApplicationId());
        String raw=tokens.opaqueToken();
        RecruitmentInterviewInvitationToken token=new RecruitmentInterviewInvitationToken();
        token.setTenantId(delivery.getTenantId());token.setInterviewId(interview.getId());
        token.setApplicationId(delivery.getApplicationId());token.setDeliveryId(delivery.getId());token.setTokenHash(tokens.hash(raw));
        LocalDateTime expiry=interview.getInvitationExpiresAt();
        if(interview.getScheduledEndAt()!=null){
            LocalDateTime bookedExpiry=LocalDateTime.ofInstant(interview.getScheduledEndAt().plus(Duration.ofDays(1)),ZoneOffset.UTC);
            if(expiry==null||bookedExpiry.isAfter(expiry))expiry=bookedExpiry;
        }
        if(expiry==null||!expiry.isAfter(now))expiry=now.plusDays(7);
        token.setExpiresAt(expiry);invitationTokens.saveAndFlush(token);
        delivery.setAttempts(delivery.getAttempts()+1);delivery.setState(CandidateEmailState.DISPATCHING);
        delivery.setLastError(null);deliveries.save(delivery);
        events.publishEvent(new RecruitmentCandidateEmailDispatchEvent(delivery.getId(),recipient.email(),recipient.name(),
                recipient.company(),recipient.job(),recipient.locale(),properties.candidateBaseUrl()+"#invitation="+raw,
                delivery.getKind().name(),interview.getScheduledStartAt(),interview.getSchedulingTimezone()));
    }

    @Transactional
    @Override public void complete(UUID deliveryId,boolean success,String error){
        RecruitmentCandidateEmailDelivery delivery=deliveries.findForUpdate(deliveryId).orElse(null);
        if(delivery==null||delivery.getState()==CandidateEmailState.SENT||delivery.getState()==CandidateEmailState.CANCELLED)return;
        LocalDateTime now=LocalDateTime.now(clock);
        if(success){delivery.setState(CandidateEmailState.SENT);delivery.setSentAt(now);delivery.setLastError(null);}
        else {invitationTokens.revokeDelivery(deliveryId,now);delivery.setLastError(safe(error));
            if(delivery.getAttempts()>=10)delivery.setState(CandidateEmailState.FAILED);
            else {delivery.setState(CandidateEmailState.FAILED);long seconds=Math.min(3600,1L<<Math.min(delivery.getAttempts(),10));delivery.setNextAttemptAt(now.plusSeconds(seconds));}}
        deliveries.save(delivery);
    }

    private static String safe(String value){if(value==null)return "Delivery failed";String clean=value.replaceAll("[\\r\\n]"," ");return clean.substring(0,Math.min(500,clean.length()));}
}

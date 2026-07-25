package com.cacanode.api.recruitment.listener;

import com.cacanode.api.recruitment.config.RecruitmentRabbitTopology;
import com.cacanode.api.recruitment.service.RecruitmentInterviewEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false}")
public class RecruitmentCvAnalysisOutcomeListener {
    private final RecruitmentInterviewEventDispatcher dispatcher;
    @RabbitListener(queues=RecruitmentRabbitTopology.INTERVIEW_EVENTS_QUEUE,
            containerFactory="recruitmentInterviewListenerContainerFactory")
    public void receive(byte[] payload){dispatcher.accept(payload);}
}

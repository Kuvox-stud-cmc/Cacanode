package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.query.RecruitmentInterviewResultEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false}")
public class RecruitmentInterviewEventDispatcher {
    private final ObjectMapper mapper;
    private final RecruitmentInterviewResultEventService results;
    private final ObjectProvider<RecruitmentCvAnalysisOutcomeService> cvOutcomes;

    public void accept(byte[] payload) {
        String type;
        try {
            JsonNode root=mapper.readTree(payload);
            if(root==null||!root.isObject()||!root.path("event_type").isTextual())
                throw new IllegalArgumentException();
            type=root.path("event_type").asText();
        } catch(Exception exception) {
            throw new AmqpRejectAndDontRequeueException("Malformed recruitment interview event",exception);
        }
        if("interview.resume-analysis.outcome".equals(type)) {
            RecruitmentCvAnalysisOutcomeService service=cvOutcomes.getIfAvailable();
            if(service==null)throw new AmqpRejectAndDontRequeueException("CV analysis is disabled");
            service.accept(payload);return;
        }
        results.accept(payload);
    }
}

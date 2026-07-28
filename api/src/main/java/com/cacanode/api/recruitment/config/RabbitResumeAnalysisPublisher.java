package com.cacanode.api.recruitment.config;

import com.cacanode.api.recruitment.api.ResumeAnalysisPublisher;
import com.cacanode.api.recruitment.api.event.ResumeAnalysisRequestedEvent;
import com.rabbitmq.client.AMQP;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.recruitment",
        name = {"enabled", "messaging-enabled"},
        havingValue = "true")
public class RabbitResumeAnalysisPublisher implements ResumeAnalysisPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitResumeAnalysisPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ResumeAnalysisRequestedEvent event) {
        byte[] canonicalJsonPayload;
        try {
            canonicalJsonPayload = objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Resume-analysis event could not be serialized", exception);
        }
        rabbitTemplate.execute(channel -> {
            channel.confirmSelect();
            channel.basicPublish(
                    RecruitmentRabbitTopology.INTERVIEW_EXCHANGE,
                    RecruitmentRabbitTopology.RESUME_ANALYSIS_REQUESTED,
                    true,
                    new AMQP.BasicProperties.Builder()
                            .contentType("application/json")
                            .deliveryMode(2)
                            .messageId(event.eventId().toString())
                            .build(),
                    canonicalJsonPayload);
            channel.waitForConfirmsOrDie(RecruitmentRabbitTopology.CONFIRM_TIMEOUT_MILLIS);
            return null;
        });
    }
}

package com.cacanode.api.recruitment.config;

import com.rabbitmq.client.AMQP;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@ConditionalOnExpression("${app.recruitment.enabled:false} and "
        + "${app.recruitment.messaging-enabled:false} and ${app.recruitment.recording-enabled:false}")
public class RabbitRecordingOperationPublisher {
    private final RabbitTemplate rabbitTemplate;

    public RabbitRecordingOperationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(UUID operationId) {
        byte[] payload = operationId.toString().getBytes(StandardCharsets.UTF_8);
        rabbitTemplate.execute(channel -> {
            channel.confirmSelect();
            channel.basicPublish(
                    RecruitmentRabbitTopology.RECORDING_OPERATION_EXCHANGE,
                    RecruitmentRabbitTopology.RECORDING_OPERATION_REQUESTED,
                    true,
                    new AMQP.BasicProperties.Builder()
                            .contentType("text/plain")
                            .contentEncoding(StandardCharsets.UTF_8.name())
                            .deliveryMode(2)
                            .messageId(operationId.toString())
                            .type(RecruitmentRabbitTopology.RECORDING_OPERATION_REQUESTED)
                            .build(),
                    payload);
            channel.waitForConfirmsOrDie(RecruitmentRabbitTopology.CONFIRM_TIMEOUT_MILLIS);
            return null;
        });
    }
}

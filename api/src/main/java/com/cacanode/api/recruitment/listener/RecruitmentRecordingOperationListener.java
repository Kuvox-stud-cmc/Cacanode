package com.cacanode.api.recruitment.listener;

import com.cacanode.api.recruitment.config.RecruitmentRabbitTopology;
import com.cacanode.api.recruitment.repository.RecruitmentRecordingOperationWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and "
        + "${app.recruitment.messaging-enabled:false} and ${app.recruitment.recording-enabled:false}")
public class RecruitmentRecordingOperationListener {
    private final RecruitmentRecordingOperationWorker worker;

    @RabbitListener(
            queues = RecruitmentRabbitTopology.RECORDING_OPERATION_QUEUE,
            containerFactory = "recruitmentInterviewListenerContainerFactory")
    public void receive(byte[] payload) {
        String value = new String(payload, StandardCharsets.UTF_8);
        UUID operationId;
        try {
            operationId = UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed recording-operation notification", exception);
        }
        if (!operationId.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical recording-operation notification");
        }
        worker.process(operationId);
    }
}

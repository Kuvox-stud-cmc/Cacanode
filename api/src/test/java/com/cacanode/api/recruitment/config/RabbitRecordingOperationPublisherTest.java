package com.cacanode.api.recruitment.config;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitRecordingOperationPublisherTest {
    @Test
    @SuppressWarnings("unchecked")
    void publishesPersistentStableIdAndWaitsForBrokerConfirmation() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        when(template.execute(any())).thenAnswer(invocation ->
                ((ChannelCallback<Object>) invocation.getArgument(0)).doInRabbit(channel));
        RabbitRecordingOperationPublisher publisher = new RabbitRecordingOperationPublisher(template);
        UUID operationId = UUID.fromString("350bcaa8-4817-5f23-9fb9-458b99e6b2e8");

        publisher.publish(operationId);

        var payload = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(channel).confirmSelect();
        verify(channel).basicPublish(
                eq(RecruitmentRabbitTopology.RECORDING_OPERATION_EXCHANGE),
                eq(RecruitmentRabbitTopology.RECORDING_OPERATION_REQUESTED),
                eq(true), any(), payload.capture());
        assertArrayEquals(operationId.toString().getBytes(StandardCharsets.UTF_8), payload.getValue());
        verify(channel).waitForConfirmsOrDie(RecruitmentRabbitTopology.CONFIRM_TIMEOUT_MILLIS);
    }
}

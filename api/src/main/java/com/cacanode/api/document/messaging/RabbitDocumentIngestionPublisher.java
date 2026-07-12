package com.cacanode.api.document.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RabbitDocumentIngestionPublisher implements DocumentIngestionPublisher {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(DocumentIngestRequestedEvent event) {
        try {
            CorrelationData correlationData = new CorrelationData(event.eventId().toString());
            Message message = MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(event))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.eventId().toString())
                    .setCorrelationId(event.jobId().toString())
                    .build();

            rabbitTemplate.setMandatory(true);
            rabbitTemplate.send(
                    RabbitMqTopology.INGESTION_EXCHANGE,
                    RabbitMqTopology.INGEST_REQUESTED,
                    message,
                    correlationData
            );

            var confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            if (!confirm.ack()) {
                throw new InternalServerErrorException("RabbitMQ rejected document ingestion request");
            }
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("Unable to serialize document ingestion request");
        } catch (InternalServerErrorException e) {
            throw e;
        } catch (AmqpException e) {
            throw new InternalServerErrorException("Unable to publish document ingestion request");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerErrorException("Interrupted while waiting for RabbitMQ publish confirmation");
        } catch (TimeoutException e) {
            throw new InternalServerErrorException("Timed out waiting for RabbitMQ publish confirmation");
        } catch (ExecutionException e) {
            throw new InternalServerErrorException("Unable to confirm document ingestion request publication");
        }
    }
}

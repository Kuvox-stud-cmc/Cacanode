package com.cacanode.api.document.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.cacanode.api.document.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentStatusEventListener {

    private final ObjectMapper objectMapper;
    private final DocumentService documentService;

    @RabbitListener(queues = RabbitMqTopology.STATUS_QUEUE)
    public void onMessage(Message message) {
        try {
            DocumentStatusEvent event = objectMapper.readValue(message.getBody(), DocumentStatusEvent.class);
            if (event.tenantId() == null || event.documentId() == null || event.status() == null) {
                log.warn("Ignoring malformed document status event");
                return;
            }

            boolean updated = documentService.applyStatusEvent(event);
            if (!updated) {
                log.warn(
                        "Ignoring document status event for unknown document: tenantId={}, documentId={}",
                        event.tenantId(),
                        event.documentId()
                );
            }
        } catch (Exception e) {
            log.warn("Ignoring malformed document status event: {}", e.getMessage());
        }
    }
}

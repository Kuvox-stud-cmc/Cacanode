package com.cacanode.api.integration.listener;

import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.recruitment.api.event.RecordingReadyEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentApplicationProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentInterviewProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentJobProjectionChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RecruitmentWebhookListener {
    private final WebhookService webhooks;
    private final ModuleEventInboxService inbox;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void job(RecruitmentJobProjectionChangedEvent event) {
        if (event.businessEvent() == null || !inbox.claim("integration.webhook.recruitment-job")) return;
        webhooks.enqueue(event.tenantId(), event.businessEvent(), event.jobId(), event.webhookPayload());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void application(RecruitmentApplicationProjectionChangedEvent event) {
        if (event.businessEvent() == null || !inbox.claim("integration.webhook.recruitment-application")) return;
        webhooks.enqueue(event.tenantId(), event.businessEvent(), event.applicationId(), event.webhookPayload());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void interview(RecruitmentInterviewProjectionChangedEvent event) {
        if (event.businessEvent() == null || !inbox.claim("integration.webhook.recruitment-interview")) return;
        webhooks.enqueue(event.tenantId(), event.businessEvent(), event.interviewId(), event.webhookPayload());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recording(RecordingReadyEvent event) {
        if (!inbox.claim("integration.webhook.recording-ready")) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", event.tenantId());
        payload.put("interviewId", event.sessionId());
        payload.put("callAttemptId", event.callAttemptId());
        payload.put("status", "READY");
        payload.put("occurredAt", event.occurredAt());
        payload.put("retainedUntil", event.retainedUntil());
        webhooks.enqueue(event.tenantId(), "recording.ready", event.aggregateId(), Map.copyOf(payload));
    }
}

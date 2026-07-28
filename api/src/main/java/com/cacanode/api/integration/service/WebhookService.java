package com.cacanode.api.integration.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.integration.dto.WebhookDtos;
import com.cacanode.api.integration.model.WebhookEndpoint;
import com.cacanode.api.integration.model.WebhookOutboxEvent;
import com.cacanode.api.integration.repository.WebhookEndpointRepository;
import com.cacanode.api.integration.repository.WebhookOutboxRepository;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookService {
    public static final Set<String> SUPPORTED_EVENTS = Set.of(
            "conversation.started", "conversation.closed", "ticket.created",
            "job.published", "job.paused", "job.closed", "job.archived",
            "application.submitted", "application.withdrawn", "application.under_review",
            "application.shortlisted", "application.rejected",
            "interview.invited", "interview.scheduled", "interview.rescheduled",
            "interview.started", "interview.completed", "interview.failed",
            "interview.no_answer", "interview.declined", "interview.cancelled",
            "interview.expired", "recording.ready"
    );

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookOutboxRepository outboxRepository;
    private final WebhookCryptoService cryptoService;
    private final TenantEntitlementApi tenantModuleApi;

    @Transactional(readOnly = true)
    public List<WebhookDtos.Response> list(UUID tenantId) {
        return endpointRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public WebhookDtos.Created create(UUID tenantId, WebhookDtos.UpsertRequest request) {
        requireWebhooks(tenantId);
        validate(request);
        String secret = cryptoService.generateSecret();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenantId(tenantId);
        apply(endpoint, request);
        endpoint.setEncryptedSecret(cryptoService.encrypt(secret));
        endpoint = endpointRepository.save(endpoint);
        return new WebhookDtos.Created(toResponse(endpoint), secret);
    }

    @Transactional
    public WebhookDtos.Response update(UUID tenantId, UUID endpointId, WebhookDtos.UpsertRequest request) {
        requireWebhooks(tenantId);
        validate(request);
        WebhookEndpoint endpoint = find(tenantId, endpointId);
        apply(endpoint, request);
        return toResponse(endpoint);
    }

    @Transactional
    public WebhookDtos.Created rotateSecret(UUID tenantId, UUID endpointId) {
        requireWebhooks(tenantId);
        WebhookEndpoint endpoint = find(tenantId, endpointId);
        String secret = cryptoService.generateSecret();
        endpoint.setEncryptedSecret(cryptoService.encrypt(secret));
        return new WebhookDtos.Created(toResponse(endpoint), secret);
    }

    @Transactional
    public void delete(UUID tenantId, UUID endpointId) {
        endpointRepository.delete(find(tenantId, endpointId));
    }

    @Transactional
    public void enqueue(UUID tenantId, String eventType, UUID aggregateId, Map<String, Object> payload) {
        WebhookOutboxEvent event = new WebhookOutboxEvent();
        event.setTenantId(tenantId);
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        event.setNextAttemptAt(LocalDateTime.now());
        outboxRepository.save(event);
    }

    @Transactional
    public void enqueueTest(UUID tenantId, UUID endpointId) {
        requireWebhooks(tenantId);
        find(tenantId, endpointId);
        enqueue(tenantId, "test", endpointId, Map.of("message", "CacaNode webhook test"));
    }

    private WebhookEndpoint find(UUID tenantId, UUID endpointId) {
        return endpointRepository.findByIdAndTenantId(endpointId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint was not found"));
    }

    private void validate(WebhookDtos.UpsertRequest request) {
        if (!SUPPORTED_EVENTS.containsAll(request.events())) {
            throw new BadRequestException("Webhook events are invalid");
        }
        try {
            URI uri = URI.create(request.url());
            if (uri.getHost() == null || uri.getUserInfo()!=null || uri.getFragment()!=null
                    || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new BadRequestException("Webhook URL is invalid");
        }
    }

    private void apply(WebhookEndpoint endpoint, WebhookDtos.UpsertRequest request) {
        endpoint.setName(request.name().trim());
        endpoint.setUrl(request.url().trim());
        endpoint.setEvents(request.events().stream().distinct().sorted().toList());
        endpoint.setActive(request.active());
    }

    private WebhookDtos.Response toResponse(WebhookEndpoint endpoint) {
        return new WebhookDtos.Response(
                endpoint.getId(), endpoint.getName(), endpoint.getUrl(), List.copyOf(endpoint.getEvents()),
                endpoint.isActive(), endpoint.getLastDeliveryAt(), endpoint.getLastDeliveryStatus(),
                endpoint.getCreatedAt()
        );
    }

    private void requireWebhooks(UUID tenantId) {
        if (!tenantModuleApi.getEntitlements(tenantId).webhooks()) {
            throw new BadRequestException("WEBHOOKS_REQUIRE_PRO");
        }
    }
}

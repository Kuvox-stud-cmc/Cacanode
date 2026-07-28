package com.cacanode.api.integration.service;

import com.cacanode.api.integration.model.WebhookDelivery;
import com.cacanode.api.integration.model.WebhookEndpoint;
import com.cacanode.api.integration.model.WebhookOutboxEvent;
import com.cacanode.api.integration.repository.WebhookDeliveryRepository;
import com.cacanode.api.integration.repository.WebhookEndpointRepository;
import com.cacanode.api.integration.repository.WebhookOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cacanode.api.tenant.api.TenantEntitlementApi;

@Slf4j(topic = "WEBHOOK-DISPATCHER")
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {
    private static final int MAX_ATTEMPTS = 5;

    private final WebhookOutboxRepository outboxRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookCryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final TenantEntitlementApi tenantModuleApi;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Value("${app.webhooks.allow-private-networks:false}")
    private boolean allowPrivateNetworks;

    @Scheduled(fixedDelayString = "${app.webhooks.dispatch-interval-ms:5000}")
    @Transactional
    public void dispatchDueEvents() {
        for (WebhookOutboxEvent event : outboxRepository.lockDueEvents()) {
            dispatch(event.getId());
        }
    }

    public void dispatch(java.util.UUID eventId) {
        WebhookOutboxEvent event = outboxRepository.findById(eventId).orElse(null);
        if (event == null || !event.getStatus().equals("PENDING")) {
            return;
        }
        if (!tenantModuleApi.getEntitlements(event.getTenantId()).webhooks()) {
            event.setStatus("DELIVERED");
            event.setProcessedAt(LocalDateTime.now());
            return;
        }
        List<WebhookEndpoint> endpoints = endpointRepository.findByTenantIdAndActiveTrue(event.getTenantId())
                .stream()
                .filter(endpoint -> event.getEventType().equals("test")
                        ? endpoint.getId().equals(event.getAggregateId())
                        : endpoint.getEvents().contains(event.getEventType()))
                .toList();
        boolean allSucceeded = true;
        int attempt = event.getAttemptCount() + 1;
        for (WebhookEndpoint endpoint : endpoints) {
            boolean delivered = deliver(event, endpoint, attempt);
            allSucceeded = allSucceeded && delivered;
        }
        event.setAttemptCount(attempt);
        if (allSucceeded || endpoints.isEmpty()) {
            event.setStatus("DELIVERED");
            event.setProcessedAt(LocalDateTime.now());
        } else if (attempt >= MAX_ATTEMPTS) {
            event.setStatus("FAILED");
            event.setProcessedAt(LocalDateTime.now());
        } else {
            event.setNextAttemptAt(LocalDateTime.now().plusMinutes((long) Math.pow(5, attempt - 1)));
        }
    }

    private boolean deliver(WebhookOutboxEvent event, WebhookEndpoint endpoint, int attempt) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEvent(event);
        delivery.setEndpoint(endpoint);
        delivery.setAttemptNumber(attempt);
        try {
            URI uri = URI.create(endpoint.getUrl());
            Set<String> pinned=validateDestination(uri);
            long timestamp = Instant.now().getEpochSecond();
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("id", event.getId());
            envelope.put("type", event.getEventType());
            envelope.put("createdAt", event.getCreatedAt());
            envelope.put("data", event.getPayload());
            String body = objectMapper.writeValueAsString(envelope);
            String signature = cryptoService.sign(
                    cryptoService.decrypt(endpoint.getEncryptedSecret()),
                    timestamp + "." + body
            );
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "CacaNode-Webhooks/1.0")
                    .header("X-Cacanode-Event-Id", event.getId().toString())
                    .header("X-Cacanode-Timestamp", Long.toString(timestamp))
                    .header("X-Cacanode-Signature", "v1=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            if(!pinned.equals(addresses(uri)))throw new IllegalArgumentException("DNS_REBINDING_DETECTED");
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream()
            );
            try(InputStream responseBody=response.body()){if(responseBody.readNBytes(65_537).length>65_536)throw new IllegalArgumentException("RESPONSE_TOO_LARGE");}
            delivery.setResponseStatus(response.statusCode());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            if (success) {
                delivery.setDeliveredAt(LocalDateTime.now());
            } else {
                delivery.setErrorMessage("Webhook returned HTTP " + response.statusCode());
            }
            endpoint.setLastDeliveryAt(LocalDateTime.now());
            endpoint.setLastDeliveryStatus(success ? "DELIVERED" : "FAILED");
            deliveryRepository.save(delivery);
            return success;
        } catch (Exception e) {
            delivery.setErrorMessage(errorCode(e));
            endpoint.setLastDeliveryAt(LocalDateTime.now());
            endpoint.setLastDeliveryStatus("FAILED");
            deliveryRepository.save(delivery);
            log.warn("Webhook delivery failed eventId={} endpointId={}: {}",
                    event.getId(), endpoint.getId(), delivery.getErrorMessage());
            return false;
        }
    }

    private Set<String> validateDestination(URI uri) throws Exception {
        if(uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getHost()==null)throw new IllegalArgumentException("INVALID_URI");
        if (!uri.getScheme().equals("https") && !allowPrivateNetworks) {
            throw new IllegalArgumentException("Production webhooks require HTTPS");
        }
        if (allowPrivateNetworks) {
            return addresses(uri);
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()||address.isMulticastAddress()
                    ||reserved(address)) {
                throw new IllegalArgumentException("Private webhook destinations are not allowed");
            }
        }
        return addresses(uri);
    }
    private static Set<String> addresses(URI uri)throws Exception{Set<String> values=new HashSet<>();for(InetAddress address:InetAddress.getAllByName(uri.getHost()))values.add(address.getHostAddress());if(values.isEmpty())throw new IllegalArgumentException("DNS_EMPTY");return values;}
    private static boolean reserved(InetAddress address){byte[] b=address.getAddress();if(b.length==4){int first=b[0]&255,second=b[1]&255;return first==0||first>=224||first==100&&second>=64&&second<=127||first==192&&second==0||first==198&&second>=18&&second<=19||first==198&&second==51&&((b[2]&255)==100)||first==203&&second==0&&(b[2]&255)==113;}return (b[0]&0xfe)==0xfc||(b[0]&255)==0x20&&(b[1]&255)==0x01&&(b[2]&255)==0x0d&&(b[3]&255)==0xb8;}
    private static String errorCode(Exception value){String message=value.getMessage();if(message!=null&&message.matches("[A-Z0-9_ ]{3,100}"))return message.replace(' ','_');return value.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);}
}

package com.cacanode.api.tenant.service;

import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.tenant.CustomerAnswerPromptDefaults;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerAnswerPromptServiceTest {
    private TenantRepository tenantRepository;
    private ApplicationEventPublisher eventPublisher;
    private CustomerAnswerPromptService service;
    private UUID tenantId;
    private UUID actorId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new CustomerAnswerPromptService(tenantRepository, eventPublisher);
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme");
        tenant.setUpdatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant saved = invocation.getArgument(0);
            saved.setUpdatedAt(LocalDateTime.of(2026, 7, 14, 10, 5));
            return saved;
        });
    }

    @Test
    void getReturnsStoredTenantPrompt() {
        tenant.setCustomerAnswerPrompt("Use concise product terminology.");

        var response = service.get(tenantId);

        assertEquals("Use concise product terminology.", response.prompt());
        assertFalse(response.usingDefault());
        assertEquals(LocalDateTime.of(2026, 7, 14, 10, 0), response.updatedAt());
    }

    @Test
    void updateTrimsPromptAndAuditsOnlyLengthAndResetFlag() {
        var response = service.update(tenantId, actorId, "  Use a warm tone.  ");

        assertEquals("Use a warm tone.", response.prompt());
        assertFalse(response.usingDefault());
        assertEquals("Use a warm tone.", tenant.getCustomerAnswerPrompt());

        ArgumentCaptor<AuditLogEvent> eventCaptor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AuditLogEvent event = eventCaptor.getValue();
        assertEquals(LogAction.CUSTOMER_ANSWER_PROMPT_UPDATED, event.getAction());
        assertEquals(tenantId, event.getTenantId());
        assertEquals(actorId, event.getUserId());
        assertEquals(tenantId, event.getResourceId());
        assertEquals(2, event.getMetadata().size());
        assertEquals("Use a warm tone.".length(), event.getMetadata().get("promptLength"));
        assertEquals(false, event.getMetadata().get("reset"));
        assertFalse(event.getMetadata().containsValue("Use a warm tone."));
    }

    @Test
    void blankPromptRestoresPlatformDefault() {
        tenant.setCustomerAnswerPrompt("Custom prompt");

        var response = service.update(tenantId, actorId, " \n\t ");

        assertEquals(CustomerAnswerPromptDefaults.forTenant("Acme"), response.prompt());
        assertTrue(response.usingDefault());
        ArgumentCaptor<AuditLogEvent> eventCaptor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(true, eventCaptor.getValue().getMetadata().get("reset"));
        assertEquals(
                CustomerAnswerPromptDefaults.forTenant("Acme").length(),
                eventCaptor.getValue().getMetadata().get("promptLength")
        );
    }

    @Test
    void rejectsPromptLongerThanFourThousandTrimmedCharacters() {
        String prompt = " " + "x".repeat(4001) + " ";

        assertThrows(BadRequestException.class, () -> service.update(tenantId, actorId, prompt));

        verify(tenantRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void countsUnicodeCodePointsRatherThanUtf16Units() {
        String prompt = "🙂".repeat(4000);

        var response = service.update(tenantId, actorId, prompt);

        assertEquals(prompt, response.prompt());
        ArgumentCaptor<AuditLogEvent> eventCaptor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(4000, eventCaptor.getValue().getMetadata().get("promptLength"));
    }

    @Test
    void tenantLookupCannotFallBackToAnotherTenant() {
        UUID missingTenantId = UUID.randomUUID();
        when(tenantRepository.findById(missingTenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(missingTenantId));

        verify(tenantRepository).findById(missingTenantId);
    }

    @Test
    void unexpectedBlankStoredValueUsesPlatformDefault() {
        tenant.setCustomerAnswerPrompt("   ");

        var response = service.get(tenantId);

        assertEquals(CustomerAnswerPromptDefaults.forTenant("Acme"), response.prompt());
        assertTrue(response.usingDefault());
    }

    @Test
    void legacyDefaultIsPresentedAsTenantSpecificDefault() {
        tenant.setCustomerAnswerPrompt(CustomerAnswerPromptDefaults.LEGACY_PLATFORM_DEFAULT);

        var response = service.get(tenantId);

        assertEquals(CustomerAnswerPromptDefaults.forTenant("Acme"), response.prompt());
        assertTrue(response.usingDefault());
        assertTrue(response.prompt().contains("greetings, thanks, farewells"));
        assertTrue(response.prompt().contains("Respond to every customer message politely"));
    }
}

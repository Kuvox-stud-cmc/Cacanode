package com.cacanode.api.tenant.repository;

import com.cacanode.api.ai.enums.ModelConfigStatus;
import com.cacanode.api.ai.model.ModelConfigVersion;
import com.cacanode.api.ai.repository.ModelConfigVersionRepository;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.tenant.enums.ChatbotStatus;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.enums.TenantPlan;
import com.cacanode.api.tenant.enums.TenantStatus;
import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import com.cacanode.api.tenant.enums.UserRole;
import com.cacanode.api.tenant.enums.UserStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.Ticket;
import com.cacanode.api.tenant.model.User;
import com.cacanode.api.tenant.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TicketPagingIntegrationTest {
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private ModelConfigVersionRepository modelConfigRepository;
    @Autowired
    private ChatbotRepository chatbotRepository;
    @Autowired
    private UserRepository userRepository;

    private TicketService service;
    private Tenant tenant;
    private Tenant otherTenant;
    private Chatbot chatbot;
    private Chatbot otherChatbot;
    private User assignee;

    @BeforeEach
    void setUp() {
        service = new TicketService(
                ticketRepository,
                mock(TicketNoteRepository.class),
                tenantRepository,
                chatbotRepository,
                mock(IntegrationTokenRepository.class),
                userRepository,
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(WebhookService.class)
        );
        tenant = tenant("tickets-main");
        otherTenant = tenant("tickets-other");
        ModelConfigVersion model = modelConfig();
        chatbot = chatbot(tenant, knowledgeBase(tenant, "main-kb"), model, "Main bot");
        otherChatbot = chatbot(otherTenant, knowledgeBase(otherTenant, "other-kb"), model, "Other bot");
        assignee = user(tenant, "Ada", "ada-main@example.com");
    }

    @Test
    void combinesFullFiltersWithTenantIsolationAndUnassignedSupport() {
        ticket(tenant, chatbot, assignee, TicketStatus.IN_PROGRESS, TicketPriority.HIGH, TicketSource.WIDGET, "Matching");
        ticket(tenant, chatbot, null, TicketStatus.IN_PROGRESS, TicketPriority.HIGH, TicketSource.WIDGET, "Unassigned");
        ticket(tenant, chatbot, assignee, TicketStatus.OPEN, TicketPriority.HIGH, TicketSource.WIDGET, "Wrong status");
        ticket(otherTenant, otherChatbot, null, TicketStatus.IN_PROGRESS, TicketPriority.HIGH, TicketSource.WIDGET, "Other tenant");

        var assigned = service.list(
                tenant.getId(), TicketStatus.IN_PROGRESS, TicketPriority.HIGH, TicketSource.WIDGET,
                assignee.getId(), false, 0, 50
        );
        var unassigned = service.list(
                tenant.getId(), TicketStatus.IN_PROGRESS, TicketPriority.HIGH, TicketSource.WIDGET,
                null, true, 0, 50
        );

        assertEquals(1, assigned.getTotalElements());
        assertEquals("Matching", assigned.getContent().getFirst().title());
        assertEquals(1, unassigned.getTotalElements());
        assertEquals("Unassigned", unassigned.getContent().getFirst().title());
    }

    private Tenant tenant(String slug) {
        Tenant value = new Tenant();
        value.setName(slug);
        value.setSlug(slug);
        value.setPlan(TenantPlan.PRO);
        value.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(value);
    }

    private ModelConfigVersion modelConfig() {
        ModelConfigVersion value = new ModelConfigVersion();
        value.setName("tickets-model-" + UUID.randomUUID());
        value.setVersionLabel("v1");
        value.setGenerationModelId("test-generation");
        value.setTextEmbeddingModelId("test-embedding");
        value.setTextEmbeddingDimension(3);
        value.setStatus(ModelConfigStatus.ACTIVE);
        return modelConfigRepository.save(value);
    }

    private KnowledgeBase knowledgeBase(Tenant owner, String slug) {
        KnowledgeBase value = new KnowledgeBase();
        value.setTenant(owner);
        value.setName(slug);
        value.setSlug(slug);
        value.setStatus(KnowledgeBaseStatus.ACTIVE);
        return knowledgeBaseRepository.save(value);
    }

    private Chatbot chatbot(Tenant owner, KnowledgeBase knowledgeBase, ModelConfigVersion model, String name) {
        Chatbot value = new Chatbot();
        value.setTenant(owner);
        value.setKnowledgeBase(knowledgeBase);
        value.setModelConfigVersion(model);
        value.setDisplayName(name);
        value.setWelcomeMessage("Welcome");
        value.setSafeInstructions("Stay safe");
        value.setStatus(ChatbotStatus.ACTIVE);
        return chatbotRepository.save(value);
    }

    private User user(Tenant owner, String name, String email) {
        User value = new User();
        value.setTenant(owner);
        value.setFullName(name);
        value.setEmail(email);
        value.setPasswordHash("hash");
        value.setRole(UserRole.USER);
        value.setStatus(UserStatus.ACTIVE);
        return userRepository.save(value);
    }

    private void ticket(
            Tenant owner,
            Chatbot ownerChatbot,
            User assignedTo,
            TicketStatus status,
            TicketPriority priority,
            TicketSource source,
            String title
    ) {
        Ticket value = new Ticket();
        value.setTenant(owner);
        value.setChatbot(ownerChatbot);
        value.setChatSessionId(UUID.randomUUID());
        value.setExternalUserId("external-1");
        value.setCustomerEmail("customer@example.com");
        value.setTitle(title);
        value.setDescription("Description");
        value.setStatus(status);
        value.setPriority(priority);
        value.setSource(source);
        value.setAssignedTo(assignedTo);
        ticketRepository.save(value);
    }
}

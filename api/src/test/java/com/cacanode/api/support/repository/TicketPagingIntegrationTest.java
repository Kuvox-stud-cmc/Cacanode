package com.cacanode.api.support.repository;

import com.cacanode.api.ai.enums.ModelConfigStatus;
import com.cacanode.api.ai.model.ModelConfigVersion;
import com.cacanode.api.ai.repository.ModelConfigVersionRepository;
import com.cacanode.api.chat.api.ChatApi;
import com.cacanode.api.tenant.api.TenantIdentityApi;
import com.cacanode.api.tenant.enums.ChatbotStatus;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;
import com.cacanode.api.support.enums.TicketPriority;
import com.cacanode.api.support.enums.TicketSource;
import com.cacanode.api.support.enums.TicketStatus;
import com.cacanode.api.tenant.enums.UserRole;
import com.cacanode.api.tenant.enums.UserStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.support.model.Ticket;
import com.cacanode.api.tenant.model.User;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.KnowledgeBaseRepository;
import com.cacanode.api.tenant.repository.ChatbotRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import com.cacanode.api.support.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TicketService service;
    private TenantIdentityApi tenantIdentityApi;
    private Tenant tenant;
    private Tenant otherTenant;
    private Chatbot chatbot;
    private Chatbot otherChatbot;
    private User assignee;

    @BeforeEach
    void setUp() {
        tenantIdentityApi = mock(TenantIdentityApi.class);
        service = new TicketService(ticketRepository, mock(TicketNoteRepository.class),
                tenantIdentityApi, mock(ChatApi.class),
                mock(org.springframework.context.ApplicationEventPublisher.class));
        tenant = tenant("tickets-main");
        otherTenant = tenant("tickets-other");
        ModelConfigVersion model = modelConfig();
        chatbot = chatbot(tenant, knowledgeBase(tenant, "main-kb"), model, "Main bot");
        otherChatbot = chatbot(otherTenant, knowledgeBase(otherTenant, "other-kb"), model, "Other bot");
        assignee = user(tenant, "Ada", "ada-main@example.com");
        when(tenantIdentityApi.requireUser(tenant.getId(), assignee.getId()))
                .thenReturn(new TenantIdentityApi.UserSnapshot(
                        assignee.getId(), tenant.getId(), assignee.getFullName(), assignee.getEmail(),
                        assignee.getRole().name(), assignee.getStatus().name()));
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

    @Test
    void searchesLiteralTextAndReturnsAccurateTotalsAcrossMoreThanOneHundredRecords() {
        java.util.List<Ticket> createdTickets = new java.util.ArrayList<>();
        for (int index = 0; index < 105; index++) {
            Ticket value = ticket(tenant, chatbot, null, TicketStatus.OPEN,
                    index == 4 ? TicketPriority.URGENT : TicketPriority.NORMAL,
                    TicketSource.WIDGET, index == 7 ? "Literal 100%_ issue" : "Issue " + index);
            createdTickets.add(value);
        }
        ticketRepository.flush();
        for (int index = 0; index < createdTickets.size(); index++) {
            Ticket value = createdTickets.get(index);
            LocalDateTime created = index < 55
                    ? LocalDateTime.parse("2026-07-14T23:59:59")
                    : LocalDateTime.parse("2026-07-15T00:00:00");
            jdbcTemplate.update("UPDATE tickets SET created_at = ?, updated_at = ? WHERE id = ?",
                    Timestamp.valueOf(created), Timestamp.valueOf(created), value.getId());
        }

        var day = service.list(tenant.getId(), null, null, null, null, false,
                0, 20, null, LocalDate.parse("2026-07-14"), LocalDate.parse("2026-07-14"),
                "created", "desc");
        var literal = service.list(tenant.getId(), null, null, null, null, false,
                0, 20, "100%_", null, null, "created", "desc");
        var priorities = service.list(tenant.getId(), null, null, null, null, false,
                0, 20, null, null, null, "priority", "desc");

        assertEquals(55, day.getTotalElements());
        assertEquals(1, literal.getTotalElements());
        assertEquals("Literal 100%_ issue", literal.getContent().getFirst().title());
        assertEquals(TicketPriority.URGENT, priorities.getContent().getFirst().priority());
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
        value.setModelConfigVersionId(model.getId());
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

    private Ticket ticket(
            Tenant owner,
            Chatbot ownerChatbot,
            User assignedTo,
            TicketStatus status,
            TicketPriority priority,
            TicketSource source,
            String title
    ) {
        Ticket value = new Ticket();
        value.setTenantId(owner.getId());
        value.setChatbotId(ownerChatbot.getId());
        value.setChatSessionId(UUID.randomUUID());
        value.setExternalUserId("external-1");
        value.setCustomerEmail("customer@example.com");
        value.setTitle(title);
        value.setDescription("Description");
        value.setStatus(status);
        value.setPriority(priority);
        value.setSource(source);
        value.setAssignedToId(assignedTo == null ? null : assignedTo.getId());
        return ticketRepository.save(value);
    }
}

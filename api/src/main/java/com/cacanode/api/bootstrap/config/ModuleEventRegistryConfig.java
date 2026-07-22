package com.cacanode.api.bootstrap.config;

import com.cacanode.api.auth.api.event.Login2FARequestedEvent;
import com.cacanode.api.auth.api.event.UserRegisteredEvent;
import com.cacanode.api.billing.api.event.BillingActivatedEvent;
import com.cacanode.api.billing.api.event.BillingNoticeEvent;
import com.cacanode.api.billing.api.event.QuotaExceededEvent;
import com.cacanode.api.billing.api.event.QuotaWarningEvent;
import com.cacanode.api.chat.api.event.ConversationClosedEvent;
import com.cacanode.api.chat.api.event.ConversationStartedEvent;
import com.cacanode.api.chat.api.event.MessageRecordedEvent;
import com.cacanode.api.chat.api.event.ConversationProjectionEvent;
import com.cacanode.api.document.api.event.DocumentProjectionEvent;
import com.cacanode.api.common.event.durable.ModuleEventTypeRegistry;
import com.cacanode.api.support.api.event.TicketCreatedEvent;
import com.cacanode.api.support.api.event.TicketStatusChangedEvent;
import com.cacanode.api.tenant.api.event.TenantCreatedEvent;
import com.cacanode.api.tenant.api.event.UserDeactivatedEvent;
import com.cacanode.api.tenant.api.event.UserInvitedEvent;
import com.cacanode.api.tenant.api.event.TenantProjectionChangedEvent;
import com.cacanode.api.tenant.api.event.UserProjectionChangedEvent;
import com.cacanode.api.tenant.api.event.InvitationProjectionChangedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ModuleEventRegistryConfig {
    @Bean
    ModuleEventTypeRegistry moduleEventTypeRegistry() {
        Map<String, Class<?>> types = Map.ofEntries(
                Map.entry("tenant.created.v1", TenantCreatedEvent.class),
                Map.entry("tenant.user.invited.v1", UserInvitedEvent.class),
                Map.entry("tenant.user.deactivated.v1", UserDeactivatedEvent.class),
                Map.entry("tenant.projection.changed.v1", TenantProjectionChangedEvent.class),
                Map.entry("tenant.user.projection.changed.v1", UserProjectionChangedEvent.class),
                Map.entry("tenant.invitation.projection.changed.v1", InvitationProjectionChangedEvent.class),
                Map.entry("auth.user.registered.v1", UserRegisteredEvent.class),
                Map.entry("auth.login-2fa.requested.v1", Login2FARequestedEvent.class),
                Map.entry("billing.activated.v1", BillingActivatedEvent.class),
                Map.entry("billing.notice.v1", BillingNoticeEvent.class),
                Map.entry("billing.quota.warning.v1", QuotaWarningEvent.class),
                Map.entry("billing.quota.exceeded.v1", QuotaExceededEvent.class),
                Map.entry("chat.conversation.started.v1", ConversationStartedEvent.class),
                Map.entry("chat.conversation.closed.v1", ConversationClosedEvent.class),
                Map.entry("chat.message.recorded.v1", MessageRecordedEvent.class),
                Map.entry("chat.conversation.projection.v1", ConversationProjectionEvent.class),
                Map.entry("document.projection.changed.v1", DocumentProjectionEvent.class),
                Map.entry("support.ticket.created.v1", TicketCreatedEvent.class),
                Map.entry("support.ticket.status-changed.v1", TicketStatusChangedEvent.class)
        );
        return (stableType, version) -> {
            Class<?> type = types.get(stableType);
            if (type == null || version != 1) {
                throw new IllegalArgumentException("Unknown module event type: " + stableType + " v" + version);
            }
            return type;
        };
    }
}

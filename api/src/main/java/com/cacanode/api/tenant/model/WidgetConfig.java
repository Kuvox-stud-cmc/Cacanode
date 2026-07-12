package com.cacanode.api.tenant.model;

import com.cacanode.api.common.model.BaseEntity;
import com.cacanode.api.tenant.enums.WidgetPosition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "widget_configs",
        indexes = {
                @Index(name = "idx_widget_config_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_widget_config_chatbot_id", columnList = "chatbot_id")
        }
)
public class WidgetConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_id", unique = true, nullable = false)
    private Chatbot chatbot;

    @Column(name = "display_name", nullable = false)
    private String displayName = "Assistant";

    @Column(name = "welcome_message", nullable = false, columnDefinition = "TEXT")
    private String welcomeMessage = "Hi! How can I help you today?";

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = "#4f46e5";

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 20)
    private WidgetPosition position = WidgetPosition.BOTTOM_RIGHT;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

}

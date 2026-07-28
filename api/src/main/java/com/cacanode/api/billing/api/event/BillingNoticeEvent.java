package com.cacanode.api.billing.api.event;

import java.util.UUID;

public record BillingNoticeEvent(UUID tenantId, String type, String title, String message) {
}

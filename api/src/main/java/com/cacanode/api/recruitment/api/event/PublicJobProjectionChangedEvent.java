package com.cacanode.api.recruitment.api.event;

import java.util.UUID;

public record PublicJobProjectionChangedEvent(UUID tenantId, UUID jobId, boolean visible) {}

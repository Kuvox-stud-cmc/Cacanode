package com.cacanode.api.tenant.api;

import com.cacanode.api.tenant.api.event.TenantCreatedEvent;
import com.cacanode.api.tenant.api.event.TenantProjectionChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantKindContractCompatibilityTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void oldTenantCreatedPayloadDefaultsToCustomer() throws Exception {
        TenantCreatedEvent event = json.readValue("""
                {"tenantId":"00000000-0000-0000-0000-000000000001",
                 "adminUserId":"00000000-0000-0000-0000-000000000002",
                 "trialStartsAt":"2026-01-01T00:00:00","trialEndsAt":"2026-01-15T00:00:00",
                 "name":"Acme","status":"TRIAL","plan":"TRIAL","maxStorageMb":10,
                 "createdAt":"2026-01-01T00:00:00"}
                """, TenantCreatedEvent.class);
        assertThat(event.kind()).isEqualTo(TenantKind.CUSTOMER);
    }

    @Test
    void oldProjectionPayloadDefaultsToCustomer() throws Exception {
        TenantProjectionChangedEvent event = json.readValue("""
                {"tenantId":"00000000-0000-0000-0000-000000000001","name":"Acme",
                 "status":"ACTIVE","plan":"PRO","maxStorageMb":10,
                 "createdAt":"2026-01-01T00:00:00","updatedAt":"2026-01-02T00:00:00"}
                """, TenantProjectionChangedEvent.class);
        assertThat(event.kind()).isEqualTo(TenantKind.CUSTOMER);
    }

    @Test
    void roleInvariantSeparatesPlatformAndCustomerIdentities() {
        assertThat(TenantRoleInvariant.isValid("PLATFORM_ADMIN", TenantKind.PLATFORM_INTERNAL)).isTrue();
        assertThat(TenantRoleInvariant.isValid("PLATFORM_ADMIN", TenantKind.CUSTOMER)).isFalse();
        assertThat(TenantRoleInvariant.isValid("TENANT_ADMIN", TenantKind.PLATFORM_INTERNAL)).isFalse();
        assertThat(TenantRoleInvariant.isValid("USER", TenantKind.CUSTOMER)).isTrue();
    }
}

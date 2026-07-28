package com.cacanode.api.tenant.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V40PlatformIdentityMigrationTest {
    @Test
    void migrationDefinesBackfillsChecksAndSingleInternalTenantIndex() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V40__platform_identity_foundation.sql"));
        assertThat(sql).contains("ADD COLUMN kind", "DEFAULT 'CUSTOMER'", "PLATFORM_INTERNAL",
                "CREATE UNIQUE INDEX uq_tenants_single_platform_internal", "WHERE kind = 'PLATFORM_INTERNAL'",
                "ADD COLUMN tenant_kind", "idx_analytics_tenant_projection_kind");
    }
}

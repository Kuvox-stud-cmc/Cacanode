package com.cacanode.api.platform;

import com.cacanode.api.platform.controller.PlatformJobController;
import com.cacanode.api.recruitment.api.RecruitmentPlatformReadApi;
import com.cacanode.api.tenant.api.TenantKindApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PlatformJobControllerTest {
    private final RecruitmentPlatformReadApi recruitment = mock(RecruitmentPlatformReadApi.class);
    private final TenantKindApi tenantKinds = mock(TenantKindApi.class);
    private final PlatformJobController controller = new PlatformJobController(recruitment, tenantKinds);

    @Test
    void isConditionalOnBothPlatformAdministrationAndRecruitment() {
        ConditionalOnProperty[] conditions = PlatformJobController.class.getAnnotationsByType(ConditionalOnProperty.class);
        assertThat(conditions).extracting(ConditionalOnProperty::prefix)
                .containsExactlyInAnyOrder("app.platform-administration", "app.recruitment");
        assertThat(conditions).allSatisfy(condition -> {
            assertThat(condition.name()).containsExactly("enabled");
            assertThat(condition.havingValue()).isEqualTo("true");
        });
    }

    @Test
    void defaultsToRecentlyUpdatedAndValidatesCustomerTenant() {
        UUID tenantId = UUID.randomUUID();
        when(recruitment.jobs(any())).thenReturn(new RecruitmentPlatformReadApi.JobPage(List.of(), 0, 20, 0));
        controller.jobs(0,20,tenantId,null,null,null,null,null,null,null,null,null,null,null,null,"updatedAt","desc");
        verify(tenantKinds).requireCustomer(tenantId);
        ArgumentCaptor<RecruitmentPlatformReadApi.JobQuery> query = ArgumentCaptor.forClass(RecruitmentPlatformReadApi.JobQuery.class);
        verify(recruitment).jobs(query.capture());
        assertThat(query.getValue().sort()).isEqualTo(RecruitmentPlatformReadApi.Sort.UPDATED_AT);
        assertThat(query.getValue().direction()).isEqualTo(RecruitmentPlatformReadApi.Direction.DESC);
    }

    @Test
    void rejectsBoundsSortDirectionSearchAndInvalidRanges() {
        assertThrows(ResponseStatusException.class, () -> jobs(-1,20,null,null,null,"updatedAt","desc"));
        assertThrows(ResponseStatusException.class, () -> jobs(0,101,null,null,null,"updatedAt","desc"));
        assertThrows(ResponseStatusException.class, () -> jobs(0,20,"x".repeat(201),null,null,"updatedAt","desc"));
        assertThrows(ResponseStatusException.class, () -> jobs(0,20,null,null,null,"unknown","desc"));
        assertThrows(ResponseStatusException.class, () -> jobs(0,20,null,null,null,"updatedAt","sideways"));
        Instant at=Instant.parse("2026-07-28T00:00:00Z");
        assertThrows(ResponseStatusException.class, () -> jobs(0,20,null,at,at,"updatedAt","desc"));
    }

    @Test
    void validatesDetailTenantAfterOwnerApiLookup() {
        UUID tenantId=UUID.randomUUID(),jobId=UUID.randomUUID();
        when(recruitment.job(jobId)).thenReturn(new RecruitmentPlatformReadApi.JobDetail(jobId,UUID.randomUUID(),tenantId,
                null,"Draft", RecruitmentPlatformReadApi.JobStatus.DRAFT,null,null,"en-US",null,null,null,
                null,null,Instant.now(),false,false,0,0,0,0,0));
        controller.job(jobId);
        verify(tenantKinds).requireCustomer(tenantId);
    }

    private RecruitmentPlatformReadApi.JobPage jobs(int page, int size, String search, Instant closingFrom,
                                                     Instant closingTo, String sort, String direction) {
        return controller.jobs(page,size,null,null,search,null,null,null,null,null,null,closingFrom,closingTo,
                null,null,sort,direction);
    }
}

package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.model.*;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.HiringQuotaConsumptionRepository;
import com.cacanode.api.billing.repository.HiringQuotaReservationRepository;
import com.cacanode.api.billing.repository.UsageMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HiringQuotaServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-23T10:15:30Z");
    private BillingSubscriptionRepository subscriptions;
    private UsageMetricsRepository usage;
    private HiringQuotaConsumptionRepository consumptions;
    private HiringQuotaReservationRepository reservations;
    private BillingProperties properties;
    private HiringQuotaService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        subscriptions = mock(BillingSubscriptionRepository.class);
        usage = mock(UsageMetricsRepository.class);
        consumptions = mock(HiringQuotaConsumptionRepository.class);
        reservations = mock(HiringQuotaReservationRepository.class);
        properties = new BillingProperties();
        service = new HiringQuotaService(subscriptions, usage, consumptions, reservations,
                new BillingPeriods(), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        tenantId = UUID.randomUUID();
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription()));
        when(reservations.sumCounted(any(), any(), any(), any())).thenReturn(0L);
        when(reservations.saveAndFlush(any())).thenAnswer(invocation -> {
            HiringQuotaReservation reservation = invocation.getArgument(0);
            reservation.setId(UUID.randomUUID());
            return reservation;
        });
    }

    @Test
    void activeJobReservationHonorsBoundaryAndReplaysSemanticRequest() {
        UUID jobId = UUID.randomUUID();
        HiringQuotaApi.Reservation first = service.reserveActiveJob(tenantId, jobId);
        HiringQuotaReservation persisted = new HiringQuotaReservation();
        persisted.setId(first.reservationId());
        persisted.setTenantId(tenantId);
        persisted.setQuotaKind(HiringQuotaKind.ACTIVE_JOB);
        persisted.setAggregateId(jobId);
        persisted.setState(HiringQuotaReservationState.RESERVED);
        persisted.setReservedAmount(1);
        when(reservations.findByTenantIdAndQuotaKindAndAggregateId(
                tenantId, HiringQuotaKind.ACTIVE_JOB, jobId)).thenReturn(Optional.of(persisted));

        HiringQuotaApi.Reservation replay = service.reserveActiveJob(tenantId, jobId);

        assertFalse(first.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(first.reservationId(), replay.reservationId());
    }

    @Test
    void verifiedApplicationReplayNeverChargesCurrentWindowAgain() {
        UUID applicationId = UUID.randomUUID();
        HiringQuotaConsumption ledger = new HiringQuotaConsumption();
        ledger.setConsumedAmount(1);
        ledger.setRemainingAmount(17);
        when(consumptions.findByTenantIdAndQuotaKindAndAggregateId(
                tenantId, HiringQuotaKind.VERIFIED_APPLICATION, applicationId)).thenReturn(Optional.of(ledger));

        HiringQuotaApi.Consumption replay = service.consumeVerifiedApplication(tenantId, applicationId);

        assertEquals(1, replay.consumedAmount());
        assertEquals(17, replay.remainingAmount());
        assertTrue(replay.idempotentReplay());
        verifyNoInteractions(usage);
    }

    @Test
    void interviewOverSettlementIsRecordedAndReturnsZeroRemaining() {
        UUID reservationId = UUID.randomUUID();
        HiringQuotaReservation reservation = reservation(
                reservationId, HiringQuotaKind.INTERVIEW_SECONDS, HiringQuotaReservationState.RESERVED, 60);
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        UsageMetrics metric = metric();
        metric.setInterviewSeconds(3_590);
        when(usage.findByTenantIDAndPeriodStart(eq(tenantId), any())).thenReturn(Optional.of(metric));

        HiringQuotaApi.Consumption result = service.settleInterviewSeconds(tenantId, reservationId, 30);

        assertEquals(3_620, metric.getInterviewSeconds());
        assertEquals(0, result.remainingAmount());
        assertEquals(HiringQuotaReservationState.SETTLED, reservation.getState());
    }

    @Test
    void missingEnterpriseHiringValueFailsClosed() {
        BillingSubscription subscription = subscription();
        EntitlementSnapshot e = properties.enterpriseEntitlements();
        subscription.setEntitlementSnapshot(new EntitlementSnapshot(
                e.maxMessages(), e.maxDocuments(), e.maxTeamMembers(), e.maxStorageMb(),
                null, e.maxVerifiedApplications(), e.maxInterviewSeconds(), e.maxCvAnalyses(),
                e.maxRecruitmentStorageBytes(), e.apiAccess(), e.webhooks(), e.advancedAnalytics(), e.customBranding()));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));

        HiringQuotaApi.HiringQuotaException exception = assertThrows(
                HiringQuotaApi.HiringQuotaException.class,
                () -> service.reserveActiveJob(tenantId, UUID.randomUUID()));

        assertEquals("HIRING_QUOTA_NOT_CONFIGURED", exception.getCode());
    }

    private BillingSubscription subscription() {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanCode(BillingPlanCode.PRO);
        subscription.setStatus(BillingStatus.ACTIVE);
        subscription.setQuotaAnchorAt(LocalDateTime.of(2026, 7, 23, 0, 0));
        subscription.setPaidThroughAt(LocalDateTime.of(2026, 8, 23, 0, 0));
        subscription.setEntitlementSnapshot(properties.proEntitlements());
        return subscription;
    }

    private HiringQuotaReservation reservation(
            UUID id, HiringQuotaKind kind, HiringQuotaReservationState state, long amount) {
        HiringQuotaReservation reservation = new HiringQuotaReservation();
        reservation.setId(id);
        reservation.setTenantId(tenantId);
        reservation.setQuotaKind(kind);
        reservation.setAggregateId(UUID.randomUUID());
        reservation.setState(state);
        reservation.setReservedAmount(amount);
        reservation.setExpiresAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(1));
        return reservation;
    }

    private UsageMetrics metric() {
        UsageMetrics metric = new UsageMetrics();
        metric.setTenantID(tenantId);
        metric.setPeriodStart(LocalDateTime.of(2026, 7, 23, 0, 0));
        metric.setPeriodEnd(LocalDateTime.of(2026, 8, 23, 0, 0));
        return metric;
    }
}

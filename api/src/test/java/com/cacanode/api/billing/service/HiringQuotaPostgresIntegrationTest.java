package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.model.EntitlementSnapshot;
import com.cacanode.api.billing.model.HiringQuotaReservation;
import com.cacanode.api.billing.model.HiringQuotaReservationState;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.HiringQuotaConsumptionRepository;
import com.cacanode.api.billing.repository.HiringQuotaReservationRepository;
import com.cacanode.api.billing.repository.UsageMetricsRepository;
import com.cacanode.api.common.cache.BusinessCache;
import com.cacanode.api.common.cache.BusinessCacheInvalidationEvent;
import com.cacanode.api.testsupport.MutableClock;
import com.cacanode.api.testsupport.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("test")
@Import(HiringQuotaPostgresIntegrationTest.ClockConfiguration.class)
@RecordApplicationEvents
class HiringQuotaPostgresIntegrationTest {
    private static final Instant INITIAL_TIME = Instant.parse("2026-07-23T10:15:30Z");
    private static final String JDBC_URL = PostgresTestContainer.createDatabase("hiring_quota");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", PostgresTestContainer::username);
        registry.add("spring.datasource.password", PostgresTestContainer::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private HiringQuotaService service;
    @Autowired
    private BillingSubscriptionRepository subscriptions;
    @Autowired
    private HiringQuotaReservationRepository reservations;
    @Autowired
    private HiringQuotaConsumptionRepository consumptions;
    @Autowired
    private UsageMetricsRepository usage;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ApplicationEvents events;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE hiring_quota_consumptions, hiring_quota_reservations, usage_metrics, billing_subscriptions, tenants CASCADE");
        clock.set(INITIAL_TIME);
        tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants(id, name, slug, plan, status) VALUES (?, ?, ?, 'PRO', 'ACTIVE')",
                tenantId, "Hiring integration tenant", "hiring-" + tenantId);
    }

    @Test
    void enforcesZeroExactAndOverLimitBoundariesAcrossEveryDimension() {
        saveSubscription(entitlements(0, 0, 0, 0, 0));
        assertExceeded("ACTIVE_JOB_QUOTA_EXCEEDED", () -> service.reserveActiveJob(tenantId, UUID.randomUUID()));
        assertExceeded("VERIFIED_APPLICATION_QUOTA_EXCEEDED", () -> service.consumeVerifiedApplication(tenantId, UUID.randomUUID()));
        assertExceeded("CV_ANALYSIS_QUOTA_EXCEEDED", () -> service.consumeCvAnalysis(tenantId, UUID.randomUUID()));
        assertExceeded("INTERVIEW_SECONDS_QUOTA_EXCEEDED", () -> service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 1));
        assertExceeded("HIRING_STORAGE_QUOTA_EXCEEDED", () -> service.reserveStorage(tenantId, UUID.randomUUID(), 1));

        replaceEntitlements(entitlements(1, 1, 10, 1, 10));
        service.reserveActiveJob(tenantId, UUID.randomUUID());
        service.consumeVerifiedApplication(tenantId, UUID.randomUUID());
        service.consumeCvAnalysis(tenantId, UUID.randomUUID());
        service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 10);
        service.reserveStorage(tenantId, UUID.randomUUID(), 10);

        assertExceeded("ACTIVE_JOB_QUOTA_EXCEEDED", () -> service.reserveActiveJob(tenantId, UUID.randomUUID()));
        assertExceeded("VERIFIED_APPLICATION_QUOTA_EXCEEDED", () -> service.consumeVerifiedApplication(tenantId, UUID.randomUUID()));
        assertExceeded("CV_ANALYSIS_QUOTA_EXCEEDED", () -> service.consumeCvAnalysis(tenantId, UUID.randomUUID()));
        assertExceeded("INTERVIEW_SECONDS_QUOTA_EXCEEDED", () -> service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 1));
        assertExceeded("HIRING_STORAGE_QUOTA_EXCEEDED", () -> service.reserveStorage(tenantId, UUID.randomUUID(), 1));
    }

    @Test
    void concurrentDistinctReservationsNeverExceedCapacity() throws Exception {
        saveSubscription(entitlements(1, 10, 100, 10, 100));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return service.reserveActiveJob(tenantId, UUID.randomUUID());
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = futures.stream().map(this::get).toList();
            assertEquals(1, results.stream().filter(HiringQuotaApi.Reservation.class::isInstance).count());
            assertEquals(1, results.stream().filter(HiringQuotaApi.HiringQuotaExceededException.class::isInstance).count());
            assertEquals(1, reservations.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateConsumptionCreatesOneLedgerAndOneCounterChange() throws Exception {
        saveSubscription(entitlements(2, 2, 100, 2, 100));
        UUID applicationId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<HiringQuotaApi.Consumption>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.consumeVerifiedApplication(tenantId, applicationId);
                }));
            }
            ready.await();
            start.countDown();
            List<HiringQuotaApi.Consumption> results = futures.stream().map(this::get).toList();
            assertEquals(1, results.stream().filter(HiringQuotaApi.Consumption::idempotentReplay).count());
            assertEquals(1, consumptions.count());
            assertEquals(1, usage.findAll().getFirst().getVerifiedApplicationCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void semanticConsumptionReplaySurvivesQuotaWindowReset() {
        saveSubscription(entitlements(2, 2, 100, 2, 100));
        UUID applicationId = UUID.randomUUID();
        assertFalse(service.consumeVerifiedApplication(tenantId, applicationId).idempotentReplay());
        clock.advance(Duration.ofDays(35));
        assertTrue(service.consumeVerifiedApplication(tenantId, applicationId).idempotentReplay());
        assertEquals(1, usage.count());
    }

    @Test
    void storageSupportsContractionExpansionFailureCommitReplayAndRelease() {
        saveSubscription(entitlements(2, 2, 100, 2, 100));
        var first = service.reserveStorage(tenantId, UUID.randomUUID(), 60);
        var contracted = service.commitStorage(tenantId, first.reservationId(), 40);
        assertEquals(60, contracted.remainingAmount());
        assertTrue(service.commitStorage(tenantId, first.reservationId(), 40).idempotentReplay());
        assertCode("HIRING_QUOTA_CONFLICTING_REPLAY",
                () -> service.commitStorage(tenantId, first.reservationId(), 41));

        var second = service.reserveStorage(tenantId, UUID.randomUUID(), 50);
        assertExceeded("HIRING_STORAGE_QUOTA_EXCEEDED",
                () -> service.commitStorage(tenantId, second.reservationId(), 61));
        assertEquals(HiringQuotaReservationState.RESERVED,
                reservations.findById(second.reservationId()).orElseThrow().getState());
        service.commitStorage(tenantId, second.reservationId(), 50);
        service.releaseStorage(tenantId, second.reservationId());
        service.releaseStorage(tenantId, second.reservationId());
        assertEquals(HiringQuotaReservationState.RELEASED,
                reservations.findById(second.reservationId()).orElseThrow().getState());
    }

    @Test
    void interviewsPermitUnderAndOverSettlementIncludingAfterReleaseOrExpiry() {
        saveSubscription(entitlements(2, 2, 100, 2, 100));
        var first = service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 60);
        assertEquals(70, service.settleInterviewSeconds(tenantId, first.reservationId(), 30).remainingAmount());
        assertTrue(service.settleInterviewSeconds(tenantId, first.reservationId(), 30).idempotentReplay());
        assertCode("HIRING_QUOTA_CONFLICTING_REPLAY",
                () -> service.settleInterviewSeconds(tenantId, first.reservationId(), 31));

        var released = service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 40);
        service.releaseInterviewSeconds(tenantId, released.reservationId());
        service.settleInterviewSeconds(tenantId, released.reservationId(), 50);

        var expired = service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 10);
        clock.advance(Duration.ofHours(25));
        var over = service.settleInterviewSeconds(tenantId, expired.reservationId(), 30);
        assertEquals(0, over.remainingAmount());
        assertEquals(110, usage.findAll().stream().mapToLong(item -> item.getInterviewSeconds()).sum());
    }

    @Test
    void downgradeBlocksGrowthButStillAllowsReleaseAndIncurredSettlement() {
        saveSubscription(entitlements(2, 2, 100, 2, 100));
        var job = service.reserveActiveJob(tenantId, UUID.randomUUID());
        var interview = service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 60);
        replaceEntitlements(entitlements(0, 0, 0, 0, 0));

        assertExceeded("ACTIVE_JOB_QUOTA_EXCEEDED", () -> service.reserveActiveJob(tenantId, UUID.randomUUID()));
        assertExceeded("INTERVIEW_SECONDS_QUOTA_EXCEEDED", () -> service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 1));
        HiringQuotaReservation jobReservation = reservations.findById(job.reservationId()).orElseThrow();
        service.releaseActiveJob(tenantId, jobReservation.getAggregateId(), job.reservationId());
        service.settleInterviewSeconds(tenantId, interview.reservationId(), 80);
        assertEquals(HiringQuotaReservationState.RELEASED,
                reservations.findById(job.reservationId()).orElseThrow().getState());
        assertEquals(HiringQuotaReservationState.SETTLED,
                reservations.findById(interview.reservationId()).orElseThrow().getState());
    }

    @Test
    void exposesStableValidationMismatchExpiryAndReplayCodes() {
        saveSubscription(entitlements(2, 2, Long.MAX_VALUE, 2, 100));
        assertCode("HIRING_QUOTA_INVALID_AMOUNT", () -> service.reserveStorage(tenantId, UUID.randomUUID(), 0));
        assertCode("HIRING_QUOTA_RESERVATION_NOT_FOUND",
                () -> service.releaseStorage(tenantId, UUID.randomUUID()));

        var storage = service.reserveStorage(tenantId, UUID.randomUUID(), 10);
        assertCode("HIRING_QUOTA_KIND_MISMATCH",
                () -> service.settleInterviewSeconds(tenantId, storage.reservationId(), 1));
        UUID otherTenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants(id, name, slug, plan, status) VALUES (?, ?, ?, 'PRO', 'ACTIVE')",
                otherTenant, "Other tenant", "other-" + otherTenant);
        BillingSubscription otherSubscription = subscription(otherTenant, entitlements(2, 2, 100, 2, 100));
        subscriptions.saveAndFlush(otherSubscription);
        assertCode("HIRING_QUOTA_TENANT_MISMATCH",
                () -> service.releaseStorage(otherTenant, storage.reservationId()));
        assertCode("HIRING_QUOTA_CONFLICTING_REPLAY",
                () -> service.reserveStorage(tenantId,
                        reservations.findById(storage.reservationId()).orElseThrow().getAggregateId(), 11));

        clock.advance(Duration.ofHours(25));
        assertCode("HIRING_QUOTA_RESERVATION_EXPIRED",
                () -> service.commitStorage(tenantId, storage.reservationId(), 10));

        replaceEntitlements(entitlements(2, 2, Long.MAX_VALUE, 2, Long.MAX_VALUE));
        service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), Long.MAX_VALUE);
        assertCode("HIRING_QUOTA_AMOUNT_OVERFLOW",
                () -> service.reserveInterviewSeconds(tenantId, UUID.randomUUID(), 1));
    }

    @Test
    void successfulMutationsInvalidateTheBillingAccountCache() {
        saveSubscription(entitlements(2, 2, 100, 2, 100));
        var job = service.reserveActiveJob(tenantId, UUID.randomUUID());
        HiringQuotaReservation persisted = reservations.findById(job.reservationId()).orElseThrow();
        service.releaseActiveJob(tenantId, persisted.getAggregateId(), job.reservationId());
        service.consumeCvAnalysis(tenantId, UUID.randomUUID());

        long invalidations = events.stream(BusinessCacheInvalidationEvent.class)
                .filter(event -> event.tenantId().equals(tenantId))
                .filter(event -> event.caches().contains(BusinessCache.BILLING_ACCOUNT))
                .count();
        assertEquals(3, invalidations);
    }

    private BillingSubscription saveSubscription(EntitlementSnapshot entitlements) {
        return subscriptions.saveAndFlush(subscription(tenantId, entitlements));
    }

    private BillingSubscription subscription(UUID subscriptionTenantId, EntitlementSnapshot entitlements) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setTenantId(subscriptionTenantId);
        subscription.setPlanCode(BillingPlanCode.PRO);
        subscription.setStatus(BillingStatus.ACTIVE);
        subscription.setCatalogVersion("2026-07-23");
        subscription.setQuotaAnchorAt(LocalDateTime.ofInstant(INITIAL_TIME, ZoneOffset.UTC));
        subscription.setPaidThroughAt(LocalDateTime.ofInstant(INITIAL_TIME, ZoneOffset.UTC).plusYears(1));
        subscription.setGraceEndsAt(LocalDateTime.ofInstant(INITIAL_TIME, ZoneOffset.UTC).plusYears(1).plusDays(3));
        subscription.setEntitlementSnapshot(entitlements);
        return subscription;
    }

    private void replaceEntitlements(EntitlementSnapshot entitlements) {
        BillingSubscription subscription = subscriptions.findByTenantId(tenantId).orElseThrow();
        subscription.setEntitlementSnapshot(entitlements);
        subscriptions.saveAndFlush(subscription);
    }

    private EntitlementSnapshot entitlements(long activeJobs, long applications, long interviewSeconds,
                                             long cvAnalyses, long storageBytes) {
        return new EntitlementSnapshot(10, 10, 10, 10, activeJobs, applications, interviewSeconds,
                cvAnalyses, storageBytes, true, true, true, true);
    }

    private void assertExceeded(String code, Runnable action) {
        HiringQuotaApi.HiringQuotaExceededException exception = assertThrows(
                HiringQuotaApi.HiringQuotaExceededException.class, action::run);
        assertEquals(code, exception.getCode());
    }

    private void assertCode(String code, Runnable action) {
        HiringQuotaApi.HiringQuotaException exception = assertThrows(
                HiringQuotaApi.HiringQuotaException.class, action::run);
        assertEquals(code, exception.getCode());
    }

    private <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(INITIAL_TIME, ZoneOffset.UTC);
        }
    }
}

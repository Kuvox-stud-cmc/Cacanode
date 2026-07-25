package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.model.*;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.HiringQuotaConsumptionRepository;
import com.cacanode.api.billing.repository.HiringQuotaReservationRepository;
import com.cacanode.api.billing.repository.UsageMetricsRepository;
import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Service
@RequiredArgsConstructor
public class HiringQuotaService implements HiringQuotaApi {
    private static final String NOT_CONFIGURED = "HIRING_QUOTA_NOT_CONFIGURED";
    private static final String INVALID_AMOUNT = "HIRING_QUOTA_INVALID_AMOUNT";
    private static final String AMOUNT_OVERFLOW = "HIRING_QUOTA_AMOUNT_OVERFLOW";
    private static final String NOT_FOUND = "HIRING_QUOTA_RESERVATION_NOT_FOUND";
    private static final String TENANT_MISMATCH = "HIRING_QUOTA_TENANT_MISMATCH";
    private static final String KIND_MISMATCH = "HIRING_QUOTA_KIND_MISMATCH";
    private static final String CONFLICT = "HIRING_QUOTA_RESERVATION_CONFLICT";
    private static final String CONFLICTING_REPLAY = "HIRING_QUOTA_CONFLICTING_REPLAY";
    private static final String EXPIRED = "HIRING_QUOTA_RESERVATION_EXPIRED";
    private static final Set<HiringQuotaReservationState> RESERVED = Set.of(HiringQuotaReservationState.RESERVED);
    private static final Set<HiringQuotaReservationState> STORAGE_COUNTED =
            Set.of(HiringQuotaReservationState.RESERVED, HiringQuotaReservationState.COMMITTED);

    private final BillingSubscriptionRepository subscriptionRepository;
    private final UsageMetricsRepository usageRepository;
    private final HiringQuotaConsumptionRepository consumptionRepository;
    private final HiringQuotaReservationRepository reservationRepository;
    private final BillingPeriods periods;
    private final BillingProperties properties;
    private final Clock clock;
    @Autowired(required = false)
    private BusinessCacheInvalidationPublisher cacheInvalidationPublisher;

    @Override
    @Transactional
    public Reservation reserveActiveJob(UUID tenantId, UUID jobId) {
        return reserve(tenantId, jobId, HiringQuotaKind.ACTIVE_JOB, 1,
                EntitlementSnapshot::maxActiveJobs, "ACTIVE_JOB_QUOTA_EXCEEDED", false, null);
    }

    @Override
    @Transactional
    public void releaseActiveJob(UUID tenantId, UUID jobId, UUID reservationId) {
        BillingSubscription subscription = lockSubscription(tenantId);
        HiringQuotaReservation reservation = reservationRepository
                .findByTenantIdAndQuotaKindAndAggregateId(tenantId, HiringQuotaKind.ACTIVE_JOB, jobId)
                .orElseThrow(() -> error(NOT_FOUND, "Active-job reservation was not found"));
        if (!reservation.getId().equals(reservationId)) {
            throw error(CONFLICTING_REPLAY, "Reservation does not match the job");
        }
        if (reservation.getState() == HiringQuotaReservationState.RELEASED) return;
        if (reservation.getState() != HiringQuotaReservationState.RESERVED) {
            throw error(CONFLICT, "Active-job reservation is terminal");
        }
        release(reservation);
        invalidate(subscription.getTenantId());
    }

    @Override
    @Transactional
    public Consumption consumeVerifiedApplication(UUID tenantId, UUID applicationId) {
        return consumeOne(tenantId, applicationId, HiringQuotaKind.VERIFIED_APPLICATION,
                EntitlementSnapshot::maxVerifiedApplications, UsageMetrics::getVerifiedApplicationCount,
                UsageMetrics::setVerifiedApplicationCount, "VERIFIED_APPLICATION_QUOTA_EXCEEDED");
    }

    @Override
    @Transactional(noRollbackFor = HiringQuotaApi.HiringQuotaExceededException.class)
    public Consumption consumeCvAnalysis(UUID tenantId, UUID analysisId) {
        return consumeOne(tenantId, analysisId, HiringQuotaKind.CV_ANALYSIS,
                EntitlementSnapshot::maxCvAnalyses, UsageMetrics::getCvAnalysisCount,
                UsageMetrics::setCvAnalysisCount, "CV_ANALYSIS_QUOTA_EXCEEDED");
    }

    @Override
    @Transactional
    public Reservation reserveStorage(UUID tenantId, UUID aggregateId, long bytes) {
        return reserve(tenantId, aggregateId, HiringQuotaKind.RECRUITMENT_STORAGE, bytes,
                EntitlementSnapshot::maxRecruitmentStorageBytes, "HIRING_STORAGE_QUOTA_EXCEEDED", true, null);
    }

    @Override
    @Transactional
    public Consumption commitStorage(UUID tenantId, UUID reservationId, long actualBytes) {
        requireNonNegative(actualBytes);
        BillingSubscription subscription = lockSubscription(tenantId);
        long limit = limit(subscription, EntitlementSnapshot::maxRecruitmentStorageBytes);
        LocalDateTime now = now();
        HiringQuotaReservation reservation = reservation(tenantId, reservationId, HiringQuotaKind.RECRUITMENT_STORAGE);
        if (reservation.getState() == HiringQuotaReservationState.COMMITTED) {
            if (reservation.getSettledAmount() != null && reservation.getSettledAmount() == actualBytes) {
                return new Consumption(actualBytes, requireRemaining(reservation), true);
            }
            throw error(CONFLICTING_REPLAY, "Storage reservation was committed with a different byte count");
        }
        if (reservation.getState() == HiringQuotaReservationState.EXPIRED || isExpired(reservation, now)) {
            expire(reservation, now);
            throw error(EXPIRED, "Storage reservation has expired");
        }
        if (reservation.getState() != HiringQuotaReservationState.RESERVED) {
            throw error(CONFLICT, "Storage reservation is no longer open");
        }
        expireStale(tenantId, now);
        long counted = reservationRepository.sumCounted(
                tenantId, HiringQuotaKind.RECRUITMENT_STORAGE, STORAGE_COUNTED, now);
        long withoutThis = subtract(counted, reservation.getReservedAmount());
        long resulting = add(withoutThis, actualBytes);
        if (resulting > limit) {
            throw exceeded("HIRING_STORAGE_QUOTA_EXCEEDED", "Recruitment storage quota exceeded");
        }
        long remaining = limit - resulting;
        reservation.setState(HiringQuotaReservationState.COMMITTED);
        reservation.setSettledAmount(actualBytes);
        reservation.setRemainingAmount(remaining);
        reservationRepository.save(reservation);
        invalidate(tenantId);
        return new Consumption(actualBytes, remaining, false);
    }

    @Override
    @Transactional
    public void releaseStorage(UUID tenantId, UUID reservationId) {
        BillingSubscription subscription = lockSubscription(tenantId);
        HiringQuotaReservation reservation = reservation(tenantId, reservationId, HiringQuotaKind.RECRUITMENT_STORAGE);
        if (reservation.getState() == HiringQuotaReservationState.RELEASED) return;
        if (reservation.getState() == HiringQuotaReservationState.SETTLED) {
            throw error(CONFLICT, "Storage reservation has an invalid state");
        }
        release(reservation);
        invalidate(subscription.getTenantId());
    }

    @Override
    @Transactional
    public Reservation reserveInterviewSeconds(UUID tenantId, UUID callAttemptId, long seconds) {
        return reserve(tenantId, callAttemptId, HiringQuotaKind.INTERVIEW_SECONDS, seconds,
                EntitlementSnapshot::maxInterviewSeconds, "INTERVIEW_SECONDS_QUOTA_EXCEEDED", true, null);
    }

    @Override
    @Transactional
    public Reservation reserveInterviewSeconds(UUID tenantId,UUID aggregateId,long seconds,LocalDateTime expiresAt){
        if(expiresAt==null||!expiresAt.isAfter(now()))throw error(INVALID_AMOUNT,"Interview reservation expiry must be in the future");
        return reserve(tenantId,aggregateId,HiringQuotaKind.INTERVIEW_SECONDS,seconds,
                EntitlementSnapshot::maxInterviewSeconds,"INTERVIEW_SECONDS_QUOTA_EXCEEDED",true,expiresAt);
    }

    @Override
    @Transactional
    public void updateInterviewReservationExpiry(UUID tenantId,UUID reservationId,LocalDateTime expiresAt){
        if(expiresAt==null||!expiresAt.isAfter(now()))throw error(INVALID_AMOUNT,"Interview reservation expiry must be in the future");
        lockSubscription(tenantId);
        HiringQuotaReservation reservation=reservation(tenantId,reservationId,HiringQuotaKind.INTERVIEW_SECONDS);
        if(reservation.getState()!=HiringQuotaReservationState.RESERVED)throw error(CONFLICT,"Interview reservation is not open");
        reservation.setExpiresAt(expiresAt);reservationRepository.save(reservation);invalidate(tenantId);
    }

    @Override
    @Transactional(readOnly=true)
    public boolean isInterviewReservationActive(UUID tenantId,UUID reservationId,long expectedSeconds){
        if(reservationId==null||expectedSeconds<=0)return false;
        return reservationRepository.findByIdAndTenantId(reservationId,tenantId)
                .filter(value->value.getQuotaKind()==HiringQuotaKind.INTERVIEW_SECONDS)
                .filter(value->value.getState()==HiringQuotaReservationState.RESERVED)
                .filter(value->value.getReservedAmount()==expectedSeconds)
                .filter(value->value.getExpiresAt()!=null&&value.getExpiresAt().isAfter(now()))
                .isPresent();
    }

    @Override
    @Transactional
    public Consumption settleInterviewSeconds(UUID tenantId, UUID reservationId, long connectedSeconds) {
        requireNonNegative(connectedSeconds);
        BillingSubscription subscription = lockSubscription(tenantId);
        long limit = limit(subscription, EntitlementSnapshot::maxInterviewSeconds);
        HiringQuotaReservation reservation = reservation(tenantId, reservationId, HiringQuotaKind.INTERVIEW_SECONDS);
        if (reservation.getState() == HiringQuotaReservationState.SETTLED) {
            if (reservation.getSettledAmount() != null && reservation.getSettledAmount() == connectedSeconds) {
                return new Consumption(connectedSeconds, requireRemaining(reservation), true);
            }
            throw error(CONFLICTING_REPLAY, "Interview reservation was settled with a different duration");
        }
        LocalDateTime now = now();
        expireStale(tenantId, now);
        BillingPeriods.Period period = periods.currentQuotaPeriod(subscription, now);
        UsageMetrics usage = usage(tenantId, period);
        long resulting = add(usage.getInterviewSeconds(), connectedSeconds);
        usage.setInterviewSeconds(resulting);
        usageRepository.save(usage);
        long remaining = Math.max(0, limit - Math.min(limit, resulting));
        reservation.setState(HiringQuotaReservationState.SETTLED);
        reservation.setSettledAmount(connectedSeconds);
        reservation.setSettlementPeriodStart(period.start());
        reservation.setSettlementPeriodEnd(period.end());
        reservation.setRemainingAmount(remaining);
        reservation.setTerminalAt(now);
        reservationRepository.save(reservation);
        invalidate(tenantId);
        return new Consumption(connectedSeconds, remaining, false);
    }

    @Override
    @Transactional
    public void releaseInterviewSeconds(UUID tenantId, UUID reservationId) {
        BillingSubscription subscription = lockSubscription(tenantId);
        HiringQuotaReservation reservation = reservation(tenantId, reservationId, HiringQuotaKind.INTERVIEW_SECONDS);
        if (reservation.getState() != HiringQuotaReservationState.RESERVED) return;
        release(reservation);
        invalidate(subscription.getTenantId());
    }

    @Scheduled(fixedDelayString = "${app.billing.hiring-reservation-reaper-ms:300000}")
    @Transactional
    public void reapExpiredReservations() {
        LocalDateTime now = now();
        for (HiringQuotaReservation reservation : reservationRepository
                .findTop200ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(HiringQuotaReservationState.RESERVED, now)) {
            expire(reservation, now);
            invalidate(reservation.getTenantId());
        }
    }

    private Consumption consumeOne(
            UUID tenantId, UUID aggregateId, HiringQuotaKind kind,
            Function<EntitlementSnapshot, Long> entitlement,
            ToLongFunction<UsageMetrics> getter, LongSetter setter, String exceededCode
    ) {
        BillingSubscription subscription = lockSubscription(tenantId);
        HiringQuotaConsumption existing = consumptionRepository
                .findByTenantIdAndQuotaKindAndAggregateId(tenantId, kind, aggregateId).orElse(null);
        if (existing != null) {
            return new Consumption(existing.getConsumedAmount(), existing.getRemainingAmount(), true);
        }
        LocalDateTime now = now();
        expireStale(tenantId, now);
        long limit = limit(subscription, entitlement);
        BillingPeriods.Period period = periods.currentQuotaPeriod(subscription, now);
        UsageMetrics usage = usage(tenantId, period);
        long current = getter.applyAsLong(usage);
        if (current >= limit) throw exceeded(exceededCode, "Hiring quota exceeded");
        long resulting = add(current, 1);
        setter.set(usage, resulting);
        usageRepository.save(usage);
        HiringQuotaConsumption consumption = new HiringQuotaConsumption();
        consumption.setTenantId(tenantId);
        consumption.setQuotaKind(kind);
        consumption.setAggregateId(aggregateId);
        consumption.setPeriodStart(period.start());
        consumption.setPeriodEnd(period.end());
        consumption.setConsumedAmount(1);
        consumption.setRemainingAmount(limit - resulting);
        consumptionRepository.save(consumption);
        invalidate(tenantId);
        return new Consumption(1, limit - resulting, false);
    }

    private Reservation reserve(
            UUID tenantId, UUID aggregateId, HiringQuotaKind kind, long amount,
            Function<EntitlementSnapshot, Long> entitlement, String exceededCode, boolean expiring,
            LocalDateTime requestedExpiry
    ) {
        requirePositive(amount);
        BillingSubscription subscription = lockSubscription(tenantId);
        HiringQuotaReservation existing = reservationRepository
                .findByTenantIdAndQuotaKindAndAggregateId(tenantId, kind, aggregateId).orElse(null);
        LocalDateTime now = now();
        if (existing != null) {
            if (existing.getState() == HiringQuotaReservationState.RESERVED && !isExpired(existing, now)) {
                if (existing.getReservedAmount() != amount) {
                    throw error(CONFLICTING_REPLAY, "Reservation was created with a different amount");
                }
                return new Reservation(existing.getId(), existing.getReservedAmount(), true);
            }
            if (existing.getState() == HiringQuotaReservationState.EXPIRED || isExpired(existing, now)) {
                expire(existing, now);
                throw error(EXPIRED, "Reservation has expired");
            }
            throw error(CONFLICT, "The aggregate already has a terminal reservation");
        }
        expireStale(tenantId, now);
        long limit = limit(subscription, entitlement);
        Set<HiringQuotaReservationState> states = kind == HiringQuotaKind.RECRUITMENT_STORAGE
                ? STORAGE_COUNTED : RESERVED;
        long counted = reservationRepository.sumCounted(tenantId, kind, states, now);
        long resulting = add(counted, amount);
        if (resulting > limit) throw exceeded(exceededCode, "Hiring quota exceeded");
        HiringQuotaReservation reservation = new HiringQuotaReservation();
        reservation.setTenantId(tenantId);
        reservation.setQuotaKind(kind);
        reservation.setAggregateId(aggregateId);
        reservation.setState(HiringQuotaReservationState.RESERVED);
        reservation.setReservedAmount(amount);
        if (expiring) reservation.setExpiresAt(requestedExpiry==null
                ?now.plusHours(properties.getHiringReservationTtlHours()):requestedExpiry);
        reservationRepository.saveAndFlush(reservation);
        invalidate(tenantId);
        return new Reservation(reservation.getId(), amount, false);
    }

    private BillingSubscription lockSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
    }

    private UsageMetrics usage(UUID tenantId, BillingPeriods.Period period) {
        return usageRepository.findByTenantIDAndPeriodStart(tenantId, period.start())
                .orElseGet(() -> {
                    UsageMetrics created = new UsageMetrics();
                    created.setTenantID(tenantId);
                    created.setPeriodYear(period.start().getYear());
                    created.setPeriodMonth(period.start().getMonthValue());
                    created.setPeriodStart(period.start());
                    created.setPeriodEnd(period.end());
                    return created;
                });
    }

    private HiringQuotaReservation reservation(UUID tenantId, UUID reservationId, HiringQuotaKind kind) {
        HiringQuotaReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> error(NOT_FOUND, "Hiring reservation was not found"));
        if (!reservation.getTenantId().equals(tenantId)) {
            throw error(TENANT_MISMATCH, "Reservation tenant does not match the operation");
        }
        if (reservation.getQuotaKind() != kind) {
            throw error(KIND_MISMATCH, "Reservation kind does not match the operation");
        }
        return reservation;
    }

    private long limit(BillingSubscription subscription, Function<EntitlementSnapshot, Long> extractor) {
        EntitlementSnapshot snapshot = subscription.getEntitlementSnapshot();
        Long value = snapshot == null ? null : extractor.apply(snapshot);
        if (value == null || value < 0) throw error(NOT_CONFIGURED, "Hiring quota is not configured");
        return value;
    }

    private void expireStale(UUID tenantId, LocalDateTime now) {
        reservationRepository.expireStale(tenantId, HiringQuotaReservationState.RESERVED,
                HiringQuotaReservationState.EXPIRED, now);
    }

    private void expire(HiringQuotaReservation reservation, LocalDateTime now) {
        if (reservation.getState() == HiringQuotaReservationState.RESERVED) {
            reservation.setState(HiringQuotaReservationState.EXPIRED);
            reservation.setTerminalAt(now);
            reservationRepository.save(reservation);
        }
    }

    private void release(HiringQuotaReservation reservation) {
        reservation.setState(HiringQuotaReservationState.RELEASED);
        reservation.setTerminalAt(now());
        reservationRepository.save(reservation);
    }

    private boolean isExpired(HiringQuotaReservation reservation, LocalDateTime now) {
        return reservation.getExpiresAt() != null && !now.isBefore(reservation.getExpiresAt())
                && reservation.getState() == HiringQuotaReservationState.RESERVED;
    }

    private long requireRemaining(HiringQuotaReservation reservation) {
        return reservation.getRemainingAmount() == null ? 0 : reservation.getRemainingAmount();
    }

    private long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw error(AMOUNT_OVERFLOW, "Hiring quota amount overflowed");
        }
    }

    private long subtract(long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException overflow) {
            throw error(AMOUNT_OVERFLOW, "Hiring quota amount overflowed");
        }
    }

    private void requirePositive(long amount) {
        if (amount <= 0) throw error(INVALID_AMOUNT, "Hiring reservation amount must be positive");
    }

    private void requireNonNegative(long amount) {
        if (amount < 0) throw error(INVALID_AMOUNT, "Hiring quota amount cannot be negative");
    }

    private HiringQuotaException error(String code, String message) {
        return new HiringQuotaException(code, message);
    }

    private HiringQuotaExceededException exceeded(String code, String message) {
        return new HiringQuotaExceededException(code, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void invalidate(UUID tenantId) {
        if (cacheInvalidationPublisher != null) cacheInvalidationPublisher.billing(tenantId);
    }

    @FunctionalInterface
    private interface LongSetter {
        void set(UsageMetrics usage, long value);
    }
}

package com.cacanode.api.billing.api;

import java.util.UUID;
import java.time.LocalDateTime;

public interface HiringQuotaApi {
    Reservation reserveActiveJob(UUID tenantId, UUID jobId);

    void releaseActiveJob(UUID tenantId, UUID jobId, UUID reservationId);

    Consumption consumeVerifiedApplication(UUID tenantId, UUID applicationId);

    Consumption consumeCvAnalysis(UUID tenantId, UUID analysisId);

    Reservation reserveStorage(UUID tenantId, UUID aggregateId, long bytes);

    Consumption commitStorage(UUID tenantId, UUID reservationId, long actualBytes);

    void releaseStorage(UUID tenantId, UUID reservationId);

    Reservation reserveInterviewSeconds(UUID tenantId, UUID callAttemptId, long seconds);

    Reservation reserveInterviewSeconds(UUID tenantId, UUID aggregateId, long seconds, LocalDateTime expiresAt);

    void updateInterviewReservationExpiry(UUID tenantId, UUID reservationId, LocalDateTime expiresAt);

    boolean isInterviewReservationActive(UUID tenantId, UUID reservationId, long expectedSeconds);

    Consumption settleInterviewSeconds(
            UUID tenantId, UUID reservationId, long connectedSeconds);

    void releaseInterviewSeconds(UUID tenantId, UUID reservationId);

    record Reservation(UUID reservationId, long reservedAmount, boolean idempotentReplay) {
    }

    record Consumption(long consumedAmount, long remainingAmount, boolean idempotentReplay) {
    }

    class HiringQuotaException extends RuntimeException {
        private final String code;

        public HiringQuotaException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    final class HiringQuotaExceededException extends HiringQuotaException {
        public HiringQuotaExceededException(String code, String message) {
            super(code, message);
        }
    }
}

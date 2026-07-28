package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.api.PaymentOrderStatus;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPaymentOrderRepository extends JpaRepository<BillingPaymentOrder, UUID> {
    Optional<BillingPaymentOrder> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<BillingPaymentOrder> findByOrderCode(long orderCode);
    Optional<BillingPaymentOrder> findByTenantIdAndClientIdempotencyKey(UUID tenantId, String key);
    Optional<BillingPaymentOrder> findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, Collection<PaymentOrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BillingPaymentOrder p where p.orderCode = :orderCode")
    Optional<BillingPaymentOrder> findByOrderCodeForUpdate(@Param("orderCode") long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BillingPaymentOrder p where p.id = :id and p.tenantId = :tenantId")
    Optional<BillingPaymentOrder> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BillingPaymentOrder p where p.id = :id")
    Optional<BillingPaymentOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select count(p) > 0 from BillingPaymentOrder p
            where p.tenantId = :tenantId and p.id <> :orderId
              and p.status = com.cacanode.api.billing.api.PaymentOrderStatus.PAID
              and p.paidAt is not null and p.paidAt >= :createdAt
            """)
    boolean existsSupersedingPaidOrder(@Param("tenantId") UUID tenantId,
                                       @Param("orderId") UUID orderId,
                                       @Param("createdAt") LocalDateTime createdAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            update BillingPaymentOrder p
            set p.status = :cancelledStatus, p.failureReason = :reason, p.updatedAt = :updatedAt
            where p.tenantId = :tenantId and p.id <> :paidOrderId and p.status in :openStatuses
            """)
    int cancelOtherOpenOrders(
            @Param("tenantId") UUID tenantId,
            @Param("paidOrderId") UUID paidOrderId,
            @Param("openStatuses") Collection<PaymentOrderStatus> openStatuses,
            @Param("cancelledStatus") PaymentOrderStatus cancelledStatus,
            @Param("reason") String reason,
            @Param("updatedAt") LocalDateTime updatedAt);

    List<BillingPaymentOrder> findTop100ByStatusInAndExpiresAtAfterOrderByCreatedAtAsc(
            Collection<PaymentOrderStatus> statuses, LocalDateTime cutoff);

    List<BillingPaymentOrder> findTop100ByStatusInOrderByCreatedAtAsc(Collection<PaymentOrderStatus> statuses);
}

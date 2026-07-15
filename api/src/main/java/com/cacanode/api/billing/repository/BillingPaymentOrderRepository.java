package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.enums.PaymentOrderStatus;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPaymentOrderRepository extends JpaRepository<BillingPaymentOrder, UUID> {
    Optional<BillingPaymentOrder> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<BillingPaymentOrder> findByTenantIdAndClientIdempotencyKey(UUID tenantId, String key);
    Optional<BillingPaymentOrder> findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, Collection<PaymentOrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BillingPaymentOrder p where p.orderCode = :orderCode")
    Optional<BillingPaymentOrder> findByOrderCodeForUpdate(@Param("orderCode") long orderCode);

    List<BillingPaymentOrder> findTop100ByStatusInAndExpiresAtAfterOrderByCreatedAtAsc(
            Collection<PaymentOrderStatus> statuses, LocalDateTime cutoff);

    List<BillingPaymentOrder> findTop100ByStatusInOrderByCreatedAtAsc(Collection<PaymentOrderStatus> statuses);
}

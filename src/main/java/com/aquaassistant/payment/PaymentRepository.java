package com.aquaassistant.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReferenceCode(String referenceCode);

    /**
     * The same payment, locked until the transaction ends: the return from PayU and its confirmation can arrive at the
     * same time, and only one of them may apply Premium; the other waits and finds the payment already approved.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.referenceCode = :referenceCode")
    Optional<Payment> findForUpdate(@org.springframework.data.repository.query.Param("referenceCode") String referenceCode);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant before);

    List<Payment> findAllByOrderByCreatedAtDesc(Pageable page);

    long countByStatusAndTestMode(PaymentStatus status, boolean testMode);

    long countByStatus(PaymentStatus status);

    boolean existsByUserIdAndStatusAndTestMode(Long userId, PaymentStatus status, boolean testMode);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status and p.testMode = :testMode")
    java.math.BigDecimal sumAmount(PaymentStatus status, boolean testMode);
}

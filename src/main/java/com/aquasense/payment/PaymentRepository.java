package com.aquasense.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReferenceCode(String referenceCode);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant before);

    List<Payment> findAllByOrderByCreatedAtDesc(Pageable page);

    long countByStatusAndTestMode(PaymentStatus status, boolean testMode);

    long countByStatus(PaymentStatus status);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status and p.testMode = :testMode")
    java.math.BigDecimal sumAmount(PaymentStatus status, boolean testMode);
}

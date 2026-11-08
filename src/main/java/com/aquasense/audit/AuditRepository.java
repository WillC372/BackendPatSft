package com.aquasense.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AuditEntry> findByActionStartingWithOrderByCreatedAtDesc(String prefix, Pageable pageable);
}

package com.aquasense.reporting.repository;

import com.aquasense.reporting.domain.ReportRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<ReportRecord, UUID> {

    List<ReportRecord> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    Optional<ReportRecord> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerIdAndCreatedAtAfter(UUID ownerId, Instant after);
}

package com.aquasense.intelligence.repository;

import com.aquasense.intelligence.domain.AnalysisRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<AnalysisRecord, UUID> {

    List<AnalysisRecord> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    long countByOwnerIdAndCreatedAtAfter(UUID ownerId, Instant after);

    Optional<AnalysisRecord> findByIdAndOwnerId(UUID id, UUID ownerId);
}

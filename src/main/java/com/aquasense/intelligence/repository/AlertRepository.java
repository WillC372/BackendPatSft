package com.aquasense.intelligence.repository;

import com.aquasense.intelligence.domain.Alert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByOwnerIdAndAcknowledgedAtIsNullOrderByMeasuredAtDesc(UUID ownerId);

    List<Alert> findByOwnerIdOrderByMeasuredAtDesc(UUID ownerId);

    Optional<Alert> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndTypeAndMeasuredAt(UUID ownerId, String type, Instant measuredAt);
}

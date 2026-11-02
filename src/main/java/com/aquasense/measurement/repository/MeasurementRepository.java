package com.aquasense.measurement.repository;

import com.aquasense.measurement.domain.Measurement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MeasurementRepository extends JpaRepository<Measurement, UUID>, JpaSpecificationExecutor<Measurement> {

    Optional<Measurement> findFirstByOwnerIdOrderByMeasuredAtDesc(UUID ownerId);

    Optional<Measurement> findFirstByDeviceIdOrderByMeasuredAtDesc(UUID deviceId);

    Optional<Measurement> findByIdAndOwnerId(UUID id, UUID ownerId);
}

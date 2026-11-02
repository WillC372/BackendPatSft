package com.aquasense.device.repository;

import com.aquasense.device.domain.Device;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByOwnerIdOrderByNameAsc(UUID ownerId);

    Optional<Device> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    boolean existsByOwnerIdAndExternalId(UUID ownerId, String externalId);
}

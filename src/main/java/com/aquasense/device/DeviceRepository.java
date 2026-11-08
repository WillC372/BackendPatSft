package com.aquasense.device;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByOwnerIdAndPort(Long ownerId, String port);

    List<Device> findByOwnerIdOrderByLastConnectedAtDesc(Long ownerId);

    List<Device> findByOwnerIdAndIdIn(Long ownerId, Collection<Long> ids);
}

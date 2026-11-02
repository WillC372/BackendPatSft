package com.aquasense.waterprofile.repository;

import com.aquasense.waterprofile.domain.WaterProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaterProfileRepository extends JpaRepository<WaterProfile, UUID> {

    @Query("select p from WaterProfile p where p.ownerId is null or p.ownerId = :userId order by p.ownerId nulls first, p.name")
    List<WaterProfile> findVisibleTo(@Param("userId") UUID userId);

    @Query("select p from WaterProfile p where p.id = :id and (p.ownerId is null or p.ownerId = :userId)")
    Optional<WaterProfile> findVisibleById(@Param("id") UUID id, @Param("userId") UUID userId);

    long countByOwnerId(UUID ownerId);

    Optional<WaterProfile> findByIdAndOwnerId(UUID id, UUID ownerId);
}

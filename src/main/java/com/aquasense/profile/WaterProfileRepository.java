package com.aquasense.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WaterProfileRepository extends JpaRepository<WaterProfile, Long> {

    @Query("select p from WaterProfile p where p.systemProfile = true or p.ownerId = :userId order by p.systemProfile desc, p.id")
    List<WaterProfile> findVisibleTo(Long userId);

    Optional<WaterProfile> findFirstBySystemProfileTrueAndType(ProfileType type);

    long countByOwnerId(Long ownerId);
}

package com.aquaassistant.place;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByOwnerIdOrderByCreatedAtAsc(Long ownerId);

    Optional<Place> findByShareCode(String shareCode);

    boolean existsByShareCode(String shareCode);

    long countByOwnerId(Long ownerId);

    /** Places the person owns or joined, oldest first. */
    @Query("select p from Place p where p.ownerId = :userId or p.id in "
        + "(select m.placeId from PlaceMember m where m.userId = :userId) order by p.createdAt asc, p.id asc")
    List<Place> findAccessibleTo(@Param("userId") Long userId);

    @Query("select count(p) > 0 from Place p where p.profileId = :profileId")
    boolean existsUsingProfile(@Param("profileId") Long profileId);

    @Query("select p from Place p where p.profileId = :profileId")
    List<Place> findUsingProfile(@Param("profileId") Long profileId);
}

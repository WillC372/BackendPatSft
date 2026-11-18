package com.aquaassistant.place;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceMemberRepository extends JpaRepository<PlaceMember, Long> {

    Optional<PlaceMember> findByPlaceIdAndUserId(Long placeId, Long userId);

    List<PlaceMember> findByPlaceIdOrderByJoinedAtAsc(Long placeId);

    long countByPlaceId(Long placeId);
}

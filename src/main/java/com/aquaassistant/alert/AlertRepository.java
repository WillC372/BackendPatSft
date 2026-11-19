package com.aquaassistant.alert;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Alert> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    /** Alerts of this kind for the person in the same place since a moment, among the given severities. */
    @Query("select count(a) from Alert a, Measurement m where m.id = a.measurementId and a.userId = :userId"
        + " and a.kind = :kind and m.placeId = :placeId and a.createdAt > :since and a.severity in :severities")
    long countRecent(Long userId, String kind, Long placeId, java.time.Instant since, java.util.Collection<String> severities);

    @Modifying
    @Query("update Alert a set a.read = true where a.userId = :userId and a.read = false")
    int markAllRead(Long userId);
}

package com.aquasense.reference;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferenceReadingRepository extends JpaRepository<ReferenceReading, Long> {

    List<ReferenceReading> findByStationIdAndMeasuredAtAfterOrderByMeasuredAtAsc(String stationId, Instant after);

    Optional<ReferenceReading> findTopByStationIdOrderByMeasuredAtDesc(String stationId);

    @Modifying
    @Query("delete from ReferenceReading r where r.measuredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}

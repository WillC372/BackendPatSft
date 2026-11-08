package com.aquasense.measurement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MeasurementRepository extends JpaRepository<Measurement, Long>, JpaSpecificationExecutor<Measurement> {

    Optional<Measurement> findTopByUserIdOrderByMeasuredAtDesc(Long userId);

    Page<Measurement> findByUserIdAndMeasuredAtBetween(Long userId, Instant from, Instant to, Pageable pageable);

    List<Measurement> findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(Long userId, Instant from, Instant to);

    List<Measurement> findTop50ByUserIdOrderByMeasuredAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByMeasuredAtAfter(Instant after);

    /** Mediciones por usuario para una página de la lista de administración (una consulta, no una por fila). */
    @org.springframework.data.jpa.repository.Query("select m.userId, count(m) from Measurement m where m.userId in :userIds group by m.userId")
    List<Object[]> countByUserIds(java.util.Collection<Long> userIds);

    @org.springframework.data.jpa.repository.Query("select m.source, count(m) from Measurement m where m.userId = :userId group by m.source")
    List<Object[]> countBySource(Long userId);
}

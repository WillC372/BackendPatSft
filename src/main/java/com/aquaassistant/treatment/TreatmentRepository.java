package com.aquaassistant.treatment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    List<Treatment> findTop200ByPlaceIdAndAppliedAtBetweenOrderByAppliedAtDesc(Long placeId, Instant from, Instant to);
}

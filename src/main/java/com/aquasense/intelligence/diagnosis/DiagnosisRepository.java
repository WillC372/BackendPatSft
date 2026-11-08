package com.aquasense.intelligence.diagnosis;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<DiagnosisRecord, Long> {

    Optional<DiagnosisRecord> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<DiagnosisRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

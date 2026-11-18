package com.aquaassistant.calibration;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalibrationRepository extends JpaRepository<Calibration, Long> {

    List<Calibration> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);
}

package com.aquasense.alert;

import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.analysis.Anomaly;
import com.aquasense.intelligence.analysis.AnomalyDetector;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementListener;
import com.aquasense.measurement.MeasurementRepository;
import com.aquasense.profile.WaterProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Measurement observer: every new measurement goes through the anomaly detector and,
 * if there are findings, an explained alert is created for the user.
 */
@Service
public class AlertService implements MeasurementListener {

    private final AlertRepository repository;
    private final MeasurementRepository measurementRepository;
    private final AnomalyDetector detector;

    public AlertService(AlertRepository repository, MeasurementRepository measurementRepository, AnomalyDetector detector) {
        this.repository = repository;
        this.measurementRepository = measurementRepository;
        this.detector = detector;
    }

    @Override
    public void onMeasurementRecorded(Measurement measurement, WaterProfile profile) {
        List<Measurement> previous = new ArrayList<>(measurementRepository.findTop50ByUserIdOrderByMeasuredAtDesc(measurement.getUserId())
            .stream()
            .filter(m -> !m.getId().equals(measurement.getId()))
            .filter(m -> !m.getMeasuredAt().isAfter(measurement.getMeasuredAt()))
            .toList());
        Collections.reverse(previous);
        for (Anomaly anomaly : detector.detect(measurement, previous, profile)) {
            repository.save(new Alert(measurement.getUserId(), measurement.getId(), anomaly.severity().name(),
                anomaly.title(), anomaly.message()));
        }
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(Long userId, boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        List<Alert> alerts = unreadOnly ? repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, page)
            : repository.findByUserIdOrderByCreatedAtDesc(userId, page);
        return alerts.stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public AlertResponse markRead(Long userId, Long alertId) {
        Alert alert = repository.findById(alertId).filter(a -> a.getUserId().equals(userId))
            .orElseThrow(() -> BusinessException.notFound("La alerta no existe."));
        alert.markRead();
        return AlertResponse.from(alert);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId);
    }
}

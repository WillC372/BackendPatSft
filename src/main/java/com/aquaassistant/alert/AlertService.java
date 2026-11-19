package com.aquaassistant.alert;

import com.aquaassistant.common.BusinessException;
import com.aquaassistant.alert.analysis.Anomaly;
import com.aquaassistant.alert.analysis.AnomalyDetector;
import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.measurement.MeasurementListener;
import com.aquaassistant.measurement.MeasurementRepository;
import com.aquaassistant.profile.WaterProfile;
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

    private com.aquaassistant.place.PlaceAccess places;
    private com.aquaassistant.notify.Notifier notifier;

    @org.springframework.beans.factory.annotation.Autowired
    void setOutsideNotifications(com.aquaassistant.place.PlaceAccess places, com.aquaassistant.notify.Notifier notifier) {
        this.places = places;
        this.notifier = notifier;
    }

    public AlertService(AlertRepository repository, MeasurementRepository measurementRepository, AnomalyDetector detector) {
        this.repository = repository;
        this.measurementRepository = measurementRepository;
        this.detector = detector;
    }

    @Override
    public void onMeasurementRecorded(Measurement measurement, WaterProfile profile) {
        List<Measurement> previous = new ArrayList<>(measurementRepository.findTop50ByPlaceIdOrderByMeasuredAtDesc(measurement.getPlaceId())
            .stream()
            .filter(m -> !m.getId().equals(measurement.getId()))
            .filter(m -> !m.getMeasuredAt().isAfter(measurement.getMeasuredAt()))
            .toList());
        Collections.reverse(previous);
        // Everyone in the place gets the alert (in the app), and important ones also outside it (phone and email).
        List<Long> people = places == null ? List.of(measurement.getUserId()) : places.people(measurement.getPlaceId());
        if (people.isEmpty()) {
            people = List.of(measurement.getUserId());
        }
        for (Anomaly anomaly : detector.detect(measurement, previous, profile)) {
            for (Long person : people) {
                repository.save(new Alert(person, measurement.getId(), anomaly.severity().name(), anomaly.title(), anomaly.message()));
                if (notifier != null) {
                    notifier.alert(person, new com.aquaassistant.notify.Notifier.Message(
                        anomaly.title(), String.format(java.util.Locale.US, "pH %.2f · %s", measurement.getPh(), anomaly.message()),
                        "/#/dashboard", "Aqua Assistant AI - " + anomaly.title(),
                        List.of(anomaly.message(), String.format(java.util.Locale.US, "Medición: pH %.2f.", measurement.getPh()),
                            "Abre Aqua Assistant AI para ver el detalle y qué hacer. Puedes desactivar estos correos en Configuración.")));
                }
            }
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

package com.aquaassistant.calibration;

import com.aquaassistant.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Saves each calibration step, whether it was done from the browser (USB) or by the server's meter. */
@Service
public class CalibrationLog {

    private final CalibrationRepository repository;
    private final AuditService audit;

    public CalibrationLog(CalibrationRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Calibration record(Long userId, String deviceName, String protocol, String point, Double bufferValue,
                              Double reading, String response, boolean success) {
        Calibration saved = repository.save(new Calibration(userId, cut(deviceName, 80, "Medidor"), cut(protocol, 30, null),
            cut(point, 10, "MID"), bufferValue, reading, cut(response, 200, null), success));
        audit.record(userId, "CALIBRATION_SAVED", saved.getDeviceName() + " · " + saved.getPoint()
            + (bufferValue == null ? "" : " (" + bufferValue + ")") + (success ? "" : " · falló"));
        return saved;
    }

    private static String cut(String value, int max, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}

package com.aquasense.measurement.service;

import com.aquasense.device.domain.Device;
import com.aquasense.device.domain.SourceKind;
import com.aquasense.device.service.DeviceService;
import com.aquasense.measurement.domain.Measurement;
import com.aquasense.measurement.domain.MeasurementRecorded;
import com.aquasense.measurement.domain.ValidationStatus;
import com.aquasense.measurement.repository.MeasurementRepository;
import com.aquasense.measurement.validation.MeasurementValidationChain;
import com.aquasense.measurement.validation.ValidationContext;
import com.aquasense.measurement.validation.ValidationContext.PreviousReading;
import com.aquasense.measurement.validation.ValidationOutcome;
import com.aquasense.shared.DomainException;
import com.aquasense.waterprofile.domain.PhClassification;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.service.WaterProfileService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeasurementService {

    private static final int MAX_PAGE_SIZE = 200;

    private final MeasurementRepository measurements;
    private final DeviceService deviceService;
    private final WaterProfileService profileService;
    private final MeasurementValidationChain validationChain;
    private final ApplicationEventPublisher events;

    public MeasurementService(MeasurementRepository measurements, DeviceService deviceService,
                              WaterProfileService profileService, MeasurementValidationChain validationChain,
                              ApplicationEventPublisher events) {
        this.measurements = measurements;
        this.deviceService = deviceService;
        this.profileService = profileService;
        this.validationChain = validationChain;
        this.events = events;
    }

    @Transactional
    public Measurement record(UUID ownerId, UUID deviceId, UUID profileId, BigDecimal rawPh, Instant measuredAt) {
        Device device = deviceService.get(deviceId, ownerId);
        WaterProfile profile = profileService.get(profileId, ownerId);
        BigDecimal ph = rawPh.setScale(2, RoundingMode.HALF_UP);
        Instant when = measuredAt == null ? Instant.now() : measuredAt;

        Optional<PreviousReading> previous = measurements.findFirstByDeviceIdOrderByMeasuredAtDesc(deviceId)
                .map(m -> new PreviousReading(m.getPh(), m.getMeasuredAt()));
        ValidationOutcome outcome = validationChain.validate(
                new ValidationContext(ph, when, Instant.now(), previous));
        if (outcome.isRejected()) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "MEASUREMENT_REJECTED",
                    String.join(" ", outcome.getNotes()));
        }

        PhClassification classification = profile.classify(ph);
        ValidationStatus status = outcome.isSuspect() ? ValidationStatus.SUSPECT : ValidationStatus.VALID;
        String notes = outcome.getNotes().isEmpty() ? null : String.join(" ", outcome.getNotes());
        SourceKind source = device.getSourceKind();
        Measurement saved = measurements.save(new Measurement(ownerId, deviceId, profileId, ph, when, source,
                classification.acidity(), classification.status(), status, notes));
        events.publishEvent(new MeasurementRecorded(saved.getId(), ownerId, deviceId, when));
        return saved;
    }

    @Transactional(readOnly = true)
    public Measurement get(UUID id, UUID ownerId) {
        return measurements.findByIdAndOwnerId(id, ownerId).orElseThrow(() ->
                new DomainException(HttpStatus.NOT_FOUND, "MEASUREMENT_NOT_FOUND", "La medición no existe."));
    }

    @Transactional(readOnly = true)
    public Optional<Measurement> latest(UUID ownerId) {
        return measurements.findFirstByOwnerIdOrderByMeasuredAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public Page<Measurement> search(UUID ownerId, Instant from, Instant to, UUID profileId, UUID deviceId,
                                    SourceKind source, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "La fecha inicial debe ser anterior a la final.");
        }
        org.springframework.data.jpa.domain.Specification<Measurement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), ownerId));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("measuredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("measuredAt"), to));
            }
            if (profileId != null) {
                predicates.add(cb.equal(root.get("profileId"), profileId));
            }
            if (deviceId != null) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("sourceKind"), source));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "measuredAt"));
        return measurements.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<Measurement> forAnalysis(UUID ownerId, UUID profileId, Instant from, Instant to,
                                         boolean includeSimulated, int limit) {
        org.springframework.data.jpa.domain.Specification<Measurement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), ownerId));
            predicates.add(cb.equal(root.get("profileId"), profileId));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("measuredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("measuredAt"), to));
            }
            if (!includeSimulated) {
                predicates.add(cb.equal(root.get("sourceKind"), SourceKind.REAL));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Measurement> newestFirst = measurements.findAll(spec,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "measuredAt"))).getContent();
        List<Measurement> ascending = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(ascending);
        return ascending;
    }
}

package com.aquasense.measurement;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.measurement.dto.NewReading;
import com.aquasense.measurement.dto.PageResponse;
import com.aquasense.measurement.dto.SeriesPoint;
import com.aquasense.measurement.validation.MeasurementValidationChain;
import com.aquasense.measurement.validation.ReadingContext;
import com.aquasense.measurement.validation.ValidationReport;
import com.aquasense.profile.Classification;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.PhStatus;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import com.aquasense.profile.WaterProfileRepository;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.aquasense.measurement.MeasurementSpecifications.fromSource;
import static com.aquasense.measurement.MeasurementSpecifications.measuredFrom;
import static com.aquasense.measurement.MeasurementSpecifications.measuredUntil;
import static com.aquasense.measurement.MeasurementSpecifications.ownedBy;
import static com.aquasense.measurement.MeasurementSpecifications.withStatus;

/**
 * Measurement Service Layer: validates, classifies, persists and notifies the observers.
 */
@Service
public class MeasurementService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MeasurementRepository repository;
    private final MeasurementValidationChain validationChain;
    private final PhClassifier classifier;
    private final ProfileService profileService;
    private final WaterProfileRepository profileRepository;
    private final MeasurementPublisher publisher;
    private final DeviceDirectory deviceDirectory;
    private final AuditService auditService;

    public MeasurementService(MeasurementRepository repository, MeasurementValidationChain validationChain,
                              PhClassifier classifier, ProfileService profileService,
                              WaterProfileRepository profileRepository, MeasurementPublisher publisher,
                              DeviceDirectory deviceDirectory, AuditService auditService) {
        this.repository = repository;
        this.validationChain = validationChain;
        this.classifier = classifier;
        this.profileService = profileService;
        this.profileRepository = profileRepository;
        this.publisher = publisher;
        this.deviceDirectory = deviceDirectory;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Preview preview(Long userId, NewReading reading) {
        WaterProfile profile = profileService.activeProfile(userId);
        ValidationReport report = validationChain.validate(contextFor(userId, reading));
        return new Preview(classifier.classify(reading.ph(), profile), report, profile);
    }

    @Transactional
    public Measurement record(Long userId, NewReading reading) {
        WaterProfile profile = profileService.activeProfile(userId);
        ValidationReport report = validationChain.validate(contextFor(userId, reading));
        if (report.isRejected()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Medición rechazada: " + String.join(" ", report.errors()));
        }
        Classification classification = classifier.classify(reading.ph(), profile);
        Measurement saved = repository.save(new Measurement(userId, reading.deviceId(), profile.getId(), reading.ph(),
            reading.temperatureC(), reading.measuredAt(), reading.source(), classification.nature(),
            classification.status(), report.status(), report.notes().isBlank() ? null : report.notes(), reading.note()));
        auditService.record(userId, "MEASUREMENT_SAVED", reading.source() + " pH " + saved.getPh());
        publisher.publish(saved, profile);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeasurementResponse> page(Long userId, Instant from, Instant to, MeasurementSource source,
                                                  PhStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Specification<Measurement> spec = Specification.where(ownedBy(userId))
            .and(measuredFrom(from)).and(measuredUntil(to)).and(fromSource(source)).and(withStatus(status));
        Page<Measurement> result = repository.findAll(spec,
            PageRequest.of(Math.max(page, 0), safeSize, Sort.by("measuredAt").descending()));
        return new PageResponse<>(toResponses(userId, result.getContent()), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Optional<MeasurementResponse> latest(Long userId) {
        return repository.findTopByUserIdOrderByMeasuredAtDesc(userId).map(m -> toResponses(userId, List.of(m)).get(0));
    }

    @Transactional(readOnly = true)
    public List<Measurement> between(Long userId, Instant from, Instant to) {
        return repository.findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, from, to);
    }

    @Transactional(readOnly = true)
    public List<SeriesPoint> series(Long userId, Instant from, Instant to) {
        return between(userId, from, to).stream()
            .map(m -> new SeriesPoint(m.getId(), m.getMeasuredAt(), m.getPh(), m.getStatus().name(), m.getSource().name()))
            .toList();
    }

    @Transactional(readOnly = true)
    public Iterator<Measurement> iterate(Long userId, Instant from, Instant to) {
        return new MeasurementIterator(repository, userId, from, to);
    }

    @Transactional(readOnly = true)
    public MeasurementResponse get(Long userId, Long id) {
        return toResponses(userId, List.of(owned(userId, id))).get(0);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Measurement measurement = owned(userId, id);
        repository.delete(measurement);
        auditService.record(userId, "MEASUREMENT_DELETED", "Medición " + id);
    }

    @Transactional(readOnly = true)
    public MeasurementResponse toResponse(Long userId, Measurement measurement) {
        return toResponses(userId, List.of(measurement)).get(0);
    }

    @Transactional(readOnly = true)
    public List<MeasurementResponse> toResponses(Long userId, List<Measurement> measurements) {
        Set<Long> profileIds = measurements.stream().map(Measurement::getProfileId).collect(Collectors.toSet());
        Map<Long, WaterProfile> profiles = profileRepository.findAllById(profileIds).stream()
            .collect(Collectors.toMap(WaterProfile::getId, Function.identity()));
        Set<Long> deviceIds = measurements.stream().map(Measurement::getDeviceId)
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deviceNames = deviceIds.isEmpty() ? Map.of() : deviceDirectory.namesFor(userId, deviceIds);
        return measurements.stream().map(m -> {
            WaterProfile profile = profiles.get(m.getProfileId());
            String explanation = classifier.explain(m.getPh(), m.getNature(), m.getStatus(), profile);
            return new MeasurementResponse(m.getId(), m.getPh(), m.getTemperatureC(), m.getMeasuredAt(),
                m.getSource().name(), m.getSource().label(), m.getNature().name(), m.getNature().label(),
                m.getStatus().name(), m.getStatus().label(), m.getValidationStatus().name(),
                m.getValidationStatus().label(), m.getValidationNotes(), explanation, profile.getId(),
                profile.getName(), m.getDeviceId(), m.getDeviceId() == null ? null : deviceNames.get(m.getDeviceId()),
                m.getNote());
        }).toList();
    }

    private Measurement owned(Long userId, Long id) {
        return repository.findById(id).filter(m -> m.getUserId().equals(userId))
            .orElseThrow(() -> BusinessException.notFound("La medición no existe."));
    }

    private ReadingContext contextFor(Long userId, NewReading reading) {
        return new ReadingContext(reading.ph(), reading.temperatureC(), reading.measuredAt(), reading.source(),
            repository.findTopByUserIdOrderByMeasuredAtDesc(userId));
    }

    public record Preview(Classification classification, ValidationReport report, WaterProfile profile) {
    }
}

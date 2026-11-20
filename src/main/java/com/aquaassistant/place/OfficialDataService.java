package com.aquaassistant.place;

import com.aquaassistant.measurement.Measurers;
import com.aquaassistant.common.BusinessException;
import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.measurement.MeasurementRepository;
import com.aquaassistant.measurement.MeasurementSource;
import com.aquaassistant.measurement.validation.ValidationStatus;
import com.aquaassistant.profile.Classification;
import com.aquaassistant.profile.PhClassifier;
import com.aquaassistant.profile.WaterProfile;
import com.aquaassistant.profile.WaterProfileRepository;
import com.aquaassistant.reference.ReferenceReading;
import com.aquaassistant.reference.ReferenceReadingRepository;
import com.aquaassistant.reference.ReferenceStation;
import com.aquaassistant.reference.ReferenceStationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the official readings of a USGS station (real pH of a river, imported by {@code ReferenceDataImporter}) into
 * a place, one per hour, with source OFFICIAL ("Dato oficial del USGS"). They let the analyses work with days of real
 * data before the person has their own; the app labels them as official, never as the person's measurements.
 * They do not go through the alerts or the validation of new readings: they are past readings of another water.
 */
@Service
public class OfficialDataService {

    static final int MAX_DAYS = 30;

    public record Result(String stationId, String stationName, int added, int alreadyThere, Instant from, Instant to) {
    }

    private final Measurers measurers;
    private final PlaceAccess places;
    private final ReferenceStationRepository stations;
    private final ReferenceReadingRepository readings;
    private final MeasurementRepository measurements;
    private final WaterProfileRepository profiles;
    private final PhClassifier classifier;

    public OfficialDataService(PlaceAccess places, ReferenceStationRepository stations, ReferenceReadingRepository readings,
                               MeasurementRepository measurements, WaterProfileRepository profiles, PhClassifier classifier, Measurers measurers) {
        this.places = places;
        this.measurers = measurers;
        this.stations = stations;
        this.readings = readings;
        this.measurements = measurements;
        this.profiles = profiles;
        this.classifier = classifier;
    }

    @Transactional
    public Result load(Long userId, Long placeId, String stationId, int days) {
        measurers.require(userId);
        Place place = places.editable(userId, placeId);
        ReferenceStation station = stations.findById(stationId == null ? "" : stationId)
            .orElseThrow(() -> BusinessException.notFound("La estación no existe en los datos oficiales."));
        WaterProfile profile = profiles.findById(place.getProfileId())
            .orElseThrow(() -> BusinessException.notFound("El perfil del lugar no existe."));
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(Math.max(1, Math.min(MAX_DAYS, days))));
        Set<Instant> taken = new HashSet<>();
        measurements.findTop2000ByPlaceIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(placeId, since, now)
            .forEach(m -> taken.add(m.getMeasuredAt().truncatedTo(ChronoUnit.HOURS)));
        String note = "Dato oficial del USGS · " + station.getId() + (station.getName() == null ? "" : " (" + station.getName() + ")");
        List<Measurement> batch = new ArrayList<>();
        int already = 0;
        Instant first = null;
        Instant last = null;
        for (ReferenceReading r : readings.findByStationIdAndMeasuredAtAfterOrderByMeasuredAtAsc(station.getId(), since)) {
            Instant hour = r.getMeasuredAt().truncatedTo(ChronoUnit.HOURS);
            if (!taken.add(hour)) {
                already++;
                continue;
            }
            double ph = r.getPh().doubleValue();
            Classification c = classifier.classify(ph, profile);
            batch.add(new Measurement(userId, null, profile.getId(), ph, null, r.getMeasuredAt(), MeasurementSource.OFFICIAL,
                c.nature(), c.status(), ValidationStatus.VALID, null, note.length() > 250 ? note.substring(0, 250) : note)
                .in(placeId, null));
            first = first == null ? r.getMeasuredAt() : first;
            last = r.getMeasuredAt();
        }
        if (batch.isEmpty() && already == 0) {
            throw BusinessException.badRequest("Esa estación no tiene datos oficiales en los últimos " + days + " días.");
        }
        measurements.saveAll(batch);
        return new Result(station.getId(), station.getName(), batch.size(), already, first, last);
    }

    /** Removes the official readings loaded into the place (the person's own measurements stay). */
    @Transactional
    public int remove(Long userId, Long placeId) {
        places.editable(userId, placeId);
        return measurements.deleteByPlaceIdAndSource(placeId, MeasurementSource.OFFICIAL);
    }
}

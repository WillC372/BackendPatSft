package com.aquasense.reference;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports official pH data from the USGS every few hours: the latest value of every station that reported in the
 * last 24 hours, and the full series (every 15 minutes) of a few featured stations. Readings older than 30 days are
 * removed so the database stays small. Each run is recorded in {@code reference_imports}.
 */
@Service
public class ReferenceDataImporter {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataImporter.class);
    static final Duration LATEST_WINDOW = Duration.ofHours(24);
    static final Duration FIRST_SERIES = Duration.ofDays(7);
    static final Duration KEEP = Duration.ofDays(30);

    private final UsgsWaterDataClient client;
    private final ReferenceStationRepository stations;
    private final ReferenceReadingRepository readings;
    private final ReferenceImportRepository imports;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final List<String> featured;

    public ReferenceDataImporter(UsgsWaterDataClient client, ReferenceStationRepository stations,
                                 ReferenceReadingRepository readings, ReferenceImportRepository imports, TransactionTemplate tx,
                                 @Value("${aquasense.reference.enabled:true}") boolean enabled,
                                 @Value("${aquasense.reference.featured:}") String featured) {
        this.client = client;
        this.stations = stations;
        this.readings = readings;
        this.imports = imports;
        this.tx = tx;
        this.enabled = enabled;
        this.featured = Arrays.stream(featured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    public List<String> featured() {
        return featured;
    }

    @Scheduled(fixedDelayString = "${aquasense.reference.interval-ms:10800000}",
        initialDelayString = "${aquasense.reference.initial-delay-ms:45000}")
    public void scheduled() {
        if (enabled) {
            refresh();
        }
    }

    /** One import. It never throws: a failure is recorded and the previous data stays available. */
    public synchronized ReferenceImport refresh() {
        ReferenceImport run = imports.save(new ReferenceImport(Instant.now()));
        int stationCount = 0;
        int readingCount = 0;
        try {
            Instant now = Instant.now();
            stationCount = importLatest(now);
            for (String id : featured) {
                readingCount += importSeries(id, now);
            }
            describeExtremes(now);
            tx.executeWithoutResult(s -> {
                stations.findByFeaturedTrueOrderByNameAsc().stream().filter(st -> !featured.contains(st.getId()))
                    .forEach(st -> st.feature(false));
                readings.deleteOlderThan(now.minus(KEEP));
            });
            run.finish(stationCount, readingCount, true, null);
            log.info("Official pH data imported: {} stations, {} new readings.", stationCount, readingCount);
        } catch (IOException | RuntimeException ex) {
            run.finish(stationCount, readingCount, false, ex.getMessage());
            log.warn("Official pH data could not be imported: {}", ex.toString());
        }
        return imports.save(run);
    }

    private int importLatest(Instant now) throws IOException {
        Map<String, UsgsWaterDataClient.PhValue> newest = client.latestPh(now.minus(LATEST_WINDOW)).stream()
            .collect(Collectors.toMap(UsgsWaterDataClient.PhValue::stationId, Function.identity(),
                (a, b) -> a.time().isAfter(b.time()) ? a : b));
        Map<String, ReferenceStation> known = stations.findAllById(newest.keySet()).stream()
            .collect(Collectors.toMap(ReferenceStation::getId, Function.identity()));
        List<ReferenceStation> changed = newest.values().stream().map(v -> {
            ReferenceStation st = known.computeIfAbsent(v.stationId(), id -> new ReferenceStation(id, "USGS"));
            st.latest(v.ph(), v.time(), v.approval(), v.latitude(), v.longitude());
            return st;
        }).toList();
        stations.saveAll(changed);
        return changed.size();
    }

    /** Names of the stations with the lowest and highest pH, which the screen shows; each one is looked up only once. */
    private void describeExtremes(Instant now) throws IOException {
        List<ReferenceStation> recent = stations.findByLastTimeAfter(now.minus(LATEST_WINDOW)).stream()
            .filter(s -> s.getLastPh() != null).sorted(Comparator.comparing(ReferenceStation::getLastPh)).toList();
        int n = recent.size();
        List<ReferenceStation> extremes = n <= 6 ? recent
            : java.util.stream.Stream.concat(recent.subList(0, 3).stream(), recent.subList(n - 3, n).stream()).toList();
        for (ReferenceStation station : extremes) {
            if (station.getName() == null) {
                client.location(station.getId()).ifPresent(l -> {
                    station.describe(l.name(), l.state(), l.siteType());
                    stations.save(station);
                });
            }
        }
    }

    private int importSeries(String id, Instant now) throws IOException {
        ReferenceStation station = stations.findById(id).orElseGet(() -> new ReferenceStation(id, "USGS"));
        if (station.getName() == null) {
            client.location(id).ifPresent(l -> {
                station.describe(l.name(), l.state(), l.siteType());
                if (station.getLatitude() == null && l.latitude() != null) {
                    station.latest(null, Instant.EPOCH, null, l.latitude(), l.longitude());
                }
            });
        }
        station.feature(true);
        stations.save(station);
        Instant from = readings.findTopByStationIdOrderByMeasuredAtDesc(id).map(r -> r.getMeasuredAt().plusSeconds(1))
            .filter(t -> t.isAfter(now.minus(FIRST_SERIES))).orElse(now.minus(FIRST_SERIES));
        List<UsgsWaterDataClient.PhValue> values = client.series(id, from);
        Set<Instant> seen = new HashSet<>();
        Map<Instant, UsgsWaterDataClient.PhValue> unique = new HashMap<>();
        values.forEach(v -> {
            if (seen.add(v.time())) {
                unique.put(v.time(), v);
            }
        });
        Set<Instant> stored = readings.findByStationIdAndMeasuredAtAfterOrderByMeasuredAtAsc(id, from.minusSeconds(1)).stream()
            .map(ReferenceReading::getMeasuredAt).collect(Collectors.toSet());
        List<ReferenceReading> fresh = unique.values().stream().sorted(Comparator.comparing(UsgsWaterDataClient.PhValue::time))
            .filter(v -> !stored.contains(v.time()))
            .map(v -> new ReferenceReading(id, v.time(), v.ph(), v.approval())).toList();
        readings.saveAll(fresh);
        unique.values().stream().max(Comparator.comparing(UsgsWaterDataClient.PhValue::time)).ifPresent(last -> {
            station.latest(last.ph(), last.time(), last.approval(), null, null);
            stations.save(station);
        });
        return fresh.size();
    }
}

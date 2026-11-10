package com.aquasense.reference;

import com.aquasense.common.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the official data for the "Datos oficiales" screen. */
@Service
public class ReferenceDataService {

    static final String SOURCE = "U.S. Geological Survey (USGS), Water Data for the Nation";
    static final String SOURCE_URL = "https://waterdata.usgs.gov/";
    static final BigDecimal LOW = new BigDecimal("6.5");
    static final BigDecimal HIGH = new BigDecimal("8.5");

    private final ReferenceStationRepository stations;
    private final ReferenceReadingRepository readings;
    private final ReferenceImportRepository imports;

    public ReferenceDataService(ReferenceStationRepository stations, ReferenceReadingRepository readings,
                                ReferenceImportRepository imports) {
        this.stations = stations;
        this.readings = readings;
        this.imports = imports;
    }

    public record Station(String id, String name, String state, String siteType, Double latitude, Double longitude,
                          BigDecimal lastPh, Instant lastTime, String approvalStatus, boolean featured, String sourceUrl) {
    }

    /**
     * {@code below}, {@code within}, {@code above}: stations under 6.5, between 6.5 and 8.5 and over 8.5 (the range
     * of the U.S. EPA secondary drinking water standard, used here only as a reference band).
     */
    public record Summary(String source, String sourceUrl, boolean importing, Instant lastImportAt, boolean lastImportOk, String lastImportMessage,
                          Instant dataUpdatedAt, int stationsReporting, BigDecimal medianPh, BigDecimal minPh, BigDecimal maxPh,
                          long below, long within, long above, List<Station> featured, List<Station> lowest, List<Station> highest) {
    }

    public record Point(Instant t, BigDecimal ph) {
    }

    public record Series(Station station, int days, List<Point> points, BigDecimal minPh, BigDecimal maxPh, BigDecimal averagePh) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        List<ReferenceStation> recent = stations.findByLastTimeAfter(Instant.now().minus(ReferenceDataImporter.LATEST_WINDOW))
            .stream().filter(s -> s.getLastPh() != null).sorted(Comparator.comparing(ReferenceStation::getLastPh)).toList();
        var last = imports.findTopByFinishedAtIsNotNullOrderByStartedAtDesc().orElse(null);
        boolean importing = imports.findTopByOrderByStartedAtDesc()
            .filter(i -> i.getFinishedAt() == null && i.getStartedAt().isAfter(Instant.now().minus(Duration.ofMinutes(15)))).isPresent();
        var lastOk = imports.findTopBySuccessTrueOrderByStartedAtDesc().orElse(null);
        BigDecimal median = null;
        if (!recent.isEmpty()) {
            int n = recent.size();
            median = n % 2 == 1 ? recent.get(n / 2).getLastPh()
                : recent.get(n / 2 - 1).getLastPh().add(recent.get(n / 2).getLastPh()).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
        }
        long below = recent.stream().filter(s -> s.getLastPh().compareTo(LOW) < 0).count();
        long above = recent.stream().filter(s -> s.getLastPh().compareTo(HIGH) > 0).count();
        List<Station> featured = stations.findByFeaturedTrueOrderByNameAsc().stream().map(ReferenceDataService::view).toList();
        return new Summary(SOURCE, SOURCE_URL, importing, last == null ? null : last.getStartedAt(), last != null && last.isSuccess(),
            last == null ? null : !last.isSuccess()
                ? "La última actualización falló (" + last.getMessage() + "); se muestran los datos anteriores."
                : last.getMessage() == null ? null : "Algunas estaciones destacadas no se pudieron actualizar en la última ronda; se muestran sus datos anteriores.",
            lastOk == null ? null : lastOk.getFinishedAt(), recent.size(), median,
            recent.isEmpty() ? null : recent.get(0).getLastPh(), recent.isEmpty() ? null : recent.get(recent.size() - 1).getLastPh(),
            below, recent.size() - below - above, above, featured,
            recent.stream().limit(3).map(ReferenceDataService::view).toList(),
            recent.reversed().stream().limit(3).map(ReferenceDataService::view).toList());
    }

    @Transactional(readOnly = true)
    public Series series(String stationId, int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        ReferenceStation station = stations.findById(stationId)
            .orElseThrow(() -> BusinessException.notFound("No hay datos de esa estación."));
        List<Point> points = readings.findByStationIdAndMeasuredAtAfterOrderByMeasuredAtAsc(stationId,
            Instant.now().minus(Duration.ofDays(safeDays))).stream().map(r -> new Point(r.getMeasuredAt(), r.getPh())).toList();
        BigDecimal min = points.stream().map(Point::ph).min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = points.stream().map(Point::ph).max(BigDecimal::compareTo).orElse(null);
        BigDecimal avg = points.isEmpty() ? null : points.stream().map(Point::ph).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(points.size()), 2, RoundingMode.HALF_UP);
        return new Series(view(station), safeDays, downsample(points), min, max, avg);
    }

    static final int MAX_POINTS = 400;

    /** Long series (a reading every 5 or 15 minutes) become at most 400 averaged points for the chart. */
    static List<Point> downsample(List<Point> points) {
        if (points.size() <= MAX_POINTS) {
            return points;
        }
        int size = (int) Math.ceil(points.size() / (double) MAX_POINTS);
        List<Point> result = new java.util.ArrayList<>();
        for (int i = 0; i < points.size(); i += size) {
            List<Point> bucket = points.subList(i, Math.min(points.size(), i + size));
            BigDecimal sum = bucket.stream().map(Point::ph).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new Point(bucket.get(bucket.size() / 2).t(), sum.divide(BigDecimal.valueOf(bucket.size()), 2, RoundingMode.HALF_UP)));
        }
        return result;
    }

    static Station view(ReferenceStation s) {
        return new Station(s.getId(), s.getName(), s.getState(), s.getSiteType(), s.getLatitude(), s.getLongitude(), s.getLastPh(),
            s.getLastTime(), s.getApprovalStatus(), s.isFeatured(),
            "https://waterdata.usgs.gov/monitoring-location/" + s.getId() + "/");
    }
}

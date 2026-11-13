package com.aquasense.measurement;

import com.aquasense.measurement.dto.ManualMeasurementRequest;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.measurement.dto.NewReading;
import com.aquasense.measurement.dto.PageResponse;
import com.aquasense.measurement.dto.SeriesPoint;
import com.aquasense.profile.PhStatus;
import com.aquasense.security.CurrentUser;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/measurements")
public class MeasurementController {

    private static final int MAX_RANGE_DAYS = 366;

    private final MeasurementService service;
    private final com.aquasense.audit.AuditService audit;

    public MeasurementController(MeasurementService service, com.aquasense.audit.AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<MeasurementResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) MeasurementSource source,
            @RequestParam(required = false) PhStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.page(CurrentUser.id(), from, to, source, status, page, size);
    }

    @GetMapping("/latest")
    public ResponseEntity<MeasurementResponse> latest() {
        return service.latest(CurrentUser.id()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/series")
    public List<SeriesPoint> series(@RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(Math.min(Math.max(days, 1), MAX_RANGE_DAYS))) : from;
        return service.series(CurrentUser.id(), start, end);
    }

    @GetMapping("/{id}")
    public MeasurementResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse createManual(@Valid @RequestBody ManualMeasurementRequest request) {
        Long userId = CurrentUser.id();
        Instant measuredAt = request.measuredAt() == null ? Instant.now() : request.measuredAt();
        Measurement saved = service.record(userId, new NewReading(request.ph(), request.temperatureC(), measuredAt,
            MeasurementSource.MANUAL, null, request.note()));
        return service.toResponse(userId, saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }

    /**
     * CSV of the measurements. With {@code from} and {@code to} it exports that period (as the history table shows it,
     * up to the maximum range); otherwise the last {@code days}. {@code source} and {@code status} filter like the table.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) MeasurementSource source,
            @RequestParam(required = false) PhStatus status) {
        Long userId = CurrentUser.id();
        boolean period = from != null && to != null;
        if (period && from.isAfter(to)) {
            throw com.aquasense.common.BusinessException.badRequest("La fecha inicial debe ser anterior a la final.");
        }
        if (!period) {
            to = Instant.now();
            from = to.minus(Duration.ofDays(Math.min(Math.max(days, 1), MAX_RANGE_DAYS)));
        } else if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            from = to.minus(Duration.ofDays(MAX_RANGE_DAYS));
        }
        StringBuilder csv = new StringBuilder("fecha_utc,ph,temperatura_c,origen,naturaleza,estado,validación,nota\n");
        Iterator<Measurement> iterator = service.iterate(userId, from, to);
        while (iterator.hasNext()) {
            Measurement m = iterator.next();
            if ((source != null && m.getSource() != source) || (status != null && m.getStatus() != status)) {
                continue;
            }
            csv.append(m.getMeasuredAt()).append(',')
                .append(String.format(Locale.US, "%.2f", m.getPh())).append(',')
                .append(m.getTemperatureC() == null ? "" : String.format(Locale.US, "%.1f", m.getTemperatureC())).append(',')
                .append(m.getSource()).append(',').append(m.getNature()).append(',').append(m.getStatus()).append(',')
                .append(m.getValidationStatus()).append(',').append(csvText(m.getNote())).append('\n');
        }
        audit.record(userId, "MEASUREMENTS_EXPORTED", period ? "CSV del " + from + " al " + to
            : "CSV de los últimos " + Math.min(Math.max(days, 1), MAX_RANGE_DAYS) + " días");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aquasense-mediciones.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csvText(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")
            ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}

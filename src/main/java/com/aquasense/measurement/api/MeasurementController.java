package com.aquasense.measurement.api;

import com.aquasense.device.domain.SourceKind;
import com.aquasense.identity.security.AuthenticatedUser;
import com.aquasense.measurement.api.MeasurementDtos.MeasurementRequest;
import com.aquasense.measurement.api.MeasurementDtos.MeasurementResponse;
import com.aquasense.measurement.api.MeasurementDtos.PageResponse;
import com.aquasense.measurement.service.MeasurementService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/measurements")
@Tag(name = "Mediciones")
@SecurityRequirement(name = "bearerAuth")
public class MeasurementController {

    private final MeasurementService service;

    public MeasurementController(MeasurementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MeasurementResponse> record(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @Valid @RequestBody MeasurementRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(MeasurementResponse.from(
                service.record(user.id(), r.deviceId(), r.profileId(), r.ph(), r.measuredAt())));
    }

    @GetMapping
    public PageResponse<MeasurementResponse> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) SourceKind source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(service.search(user.id(), from, to, profileId, deviceId, source, page, size),
                MeasurementResponse::from);
    }

    @GetMapping("/latest")
    public ResponseEntity<MeasurementResponse> latest(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.latest(user.id()).map(m -> ResponseEntity.ok(MeasurementResponse.from(m)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public MeasurementResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return MeasurementResponse.from(service.get(id, user.id()));
    }
}

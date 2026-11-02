package com.aquasense.reporting.api;

import com.aquasense.identity.security.AuthenticatedUser;
import com.aquasense.reporting.api.ReportDtos.ReportRequest;
import com.aquasense.reporting.api.ReportDtos.ReportSummary;
import com.aquasense.reporting.domain.ReportFormat;
import com.aquasense.reporting.domain.ReportRecord;
import com.aquasense.reporting.service.ReportService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/reports")
@Tag(name = "Informes")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReportSummary> generate(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody ReportRequest r) {
        ReportRecord record = service.generate(user.id(), r.profileId(), r.from(), r.to(),
                r.format() == null ? ReportFormat.MARKDOWN : r.format(), Boolean.TRUE.equals(r.includeChart()),
                r.includeSimulated() == null || r.includeSimulated());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportSummary.from(record));
    }

    @GetMapping
    public List<ReportSummary> list(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(user.id(), size).stream().map(ReportSummary::from).toList();
    }

    @GetMapping("/{id}")
    public ReportSummary get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ReportSummary.from(service.get(id, user.id()));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                          @RequestParam(defaultValue = "false") boolean download) {
        ReportRecord record = service.get(id, user.id());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.getFormat().mediaType() + ";charset=UTF-8"))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; "
                        + "img-src data:");
        if (download) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"informe-aquasense-"
                    + record.getId() + "." + record.getFormat().extension() + "\"");
        }
        return builder.body(record.getContent().getBytes(StandardCharsets.UTF_8));
    }
}

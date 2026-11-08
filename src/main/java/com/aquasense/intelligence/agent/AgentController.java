package com.aquasense.intelligence.agent;

import com.aquasense.security.CurrentUser;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intelligence/agent-reports")
public class AgentController {

    private final AgentJobService service;
    private final com.aquasense.report.pdf.ReportPdfService pdfService;

    public AgentController(AgentJobService service, com.aquasense.report.pdf.ReportPdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AgentJobResponse start(@Valid @RequestBody AgentReportRequest request) {
        return service.start(CurrentUser.id(), request);
    }

    @GetMapping("/jobs/{jobId}")
    public AgentJobResponse job(@PathVariable String jobId) {
        return service.status(CurrentUser.id(), jobId);
    }

    @GetMapping
    public List<AgentReportResponse.Summary> history(@RequestParam(defaultValue = "10") int limit) {
        return service.history(CurrentUser.id(), limit);
    }

    @GetMapping("/{id}")
    public AgentReportResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @GetMapping(value = "/{id}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<byte[]> text(@PathVariable Long id) {
        String body = service.renderText(service.get(CurrentUser.id(), id));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aquasense-informe-agente.txt\"")
            .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
            .body(body.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aquasense-informe-agente-" + id + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfService.agentReport(CurrentUser.id(), id));
    }
}

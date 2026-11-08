package com.aquasense.report;

import com.aquasense.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;
    private final com.aquasense.report.pdf.ReportPdfService pdfService;

    public ReportController(ReportService service, com.aquasense.report.pdf.ReportPdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    @GetMapping
    public Report report(@RequestParam(defaultValue = "7") int days, @RequestParam(defaultValue = "false") boolean includeAi) {
        return service.build(CurrentUser.id(), days, includeAi);
    }

    @GetMapping(value = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<byte[]> text(@RequestParam(defaultValue = "7") int days,
                                       @RequestParam(defaultValue = "false") boolean includeAi) {
        String body = service.renderText(service.build(CurrentUser.id(), days, includeAi));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aquasense-informe.txt\"")
            .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
            .body(body.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@RequestParam(defaultValue = "7") int days,
                                      @RequestParam(defaultValue = "false") boolean includeAi) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aquasense-informe.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfService.ruleReport(CurrentUser.id(), days, includeAi));
    }
}

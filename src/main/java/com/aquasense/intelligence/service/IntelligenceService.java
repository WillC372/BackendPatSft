package com.aquasense.intelligence.service;

import com.aquasense.intelligence.domain.Alert;
import com.aquasense.intelligence.domain.AnalysisRecord;
import com.aquasense.intelligence.engine.AnalysisReport;
import com.aquasense.intelligence.repository.AlertRepository;
import com.aquasense.intelligence.repository.AnalysisRepository;
import com.aquasense.shared.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntelligenceService {

    private final AnalysisUseCase useCase;
    private final AnalysisRepository analyses;
    private final AlertRepository alerts;
    private final ObjectMapper mapper;

    public IntelligenceService(AnalysisUseCase useCase, AnalysisRepository analyses, AlertRepository alerts,
                               ObjectMapper mapper) {
        this.useCase = useCase;
        this.analyses = analyses;
        this.alerts = alerts;
        this.mapper = mapper;
    }

    @Transactional
    public AnalysisResult analyze(AnalysisCommand command) {
        if (command.from() != null && command.to() != null && command.from().isAfter(command.to())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "La fecha inicial debe ser anterior a la final.");
        }
        return useCase.run(command);
    }

    @Transactional(readOnly = true)
    public List<AnalysisRecord> history(UUID ownerId, int size) {
        return analyses.findByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, Math.min(Math.max(size, 1), 50)));
    }

    @Transactional(readOnly = true)
    public AnalysisReport stored(UUID id, UUID ownerId) {
        AnalysisRecord record = analyses.findByIdAndOwnerId(id, ownerId).orElseThrow(() ->
                new DomainException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "El análisis no existe."));
        try {
            return mapper.readValue(record.getReportJson(), AnalysisReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Informe almacenado ilegible", e);
        }
    }

    @Transactional(readOnly = true)
    public List<Alert> alerts(UUID ownerId, boolean onlyOpen) {
        return onlyOpen ? alerts.findByOwnerIdAndAcknowledgedAtIsNullOrderByMeasuredAtDesc(ownerId)
                : alerts.findByOwnerIdOrderByMeasuredAtDesc(ownerId);
    }

    @Transactional
    public Alert acknowledge(UUID alertId, UUID ownerId) {
        Alert alert = alerts.findByIdAndOwnerId(alertId, ownerId).orElseThrow(() ->
                new DomainException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "La alerta no existe."));
        if (alert.getAcknowledgedAt() == null) {
            alert.acknowledge(Instant.now());
        }
        return alerts.save(alert);
    }
}

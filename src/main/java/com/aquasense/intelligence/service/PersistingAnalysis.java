package com.aquasense.intelligence.service;

import com.aquasense.intelligence.domain.AnalysisRecord;
import com.aquasense.intelligence.repository.AnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PersistingAnalysis implements AnalysisUseCase {

    private final AnalysisUseCase delegate;
    private final AnalysisRepository repository;
    private final ObjectMapper mapper;

    public PersistingAnalysis(AnalysisUseCase delegate, AnalysisRepository repository, ObjectMapper mapper) {
        this.delegate = delegate;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AnalysisResult run(AnalysisCommand command) {
        AnalysisResult result = delegate.run(command);
        try {
            AnalysisRecord record = repository.save(new AnalysisRecord(command.ownerId(), command.profileId(),
                    command.from(), command.to(), result.report().measurementCount(),
                    result.report().sufficientData(), mapper.writeValueAsString(result.report())));
            return result.withPersistence(record.getId(), record.getCreatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el informe de análisis", e);
        }
    }
}

package com.aquasense.intelligence.ai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda cada llamada a la IA en su propia transacción: si la operación que la usó falla y se revierte,
 * el consumo (tokens ya gastados) y los intentos fallidos quedan registrados igual.
 */
@Component
public class AiUsageRecorder {

    private final AiUsageRepository repository;

    public AiUsageRecorder(AiUsageRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AiUsage usage) {
        repository.save(usage);
    }
}

package com.aquasense.intelligence.ai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores every AI call in its own transaction: if the operation that used it fails and rolls back,
 * the usage (tokens already spent) and the failed attempts are still recorded.
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

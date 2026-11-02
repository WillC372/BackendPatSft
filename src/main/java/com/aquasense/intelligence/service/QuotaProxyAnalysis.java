package com.aquasense.intelligence.service;

import com.aquasense.billing.service.LimitGuard;

public class QuotaProxyAnalysis implements AnalysisUseCase {

    private final AnalysisUseCase target;
    private final LimitGuard guard;

    public QuotaProxyAnalysis(AnalysisUseCase target, LimitGuard guard) {
        this.target = target;
        this.guard = guard;
    }

    @Override
    public AnalysisResult run(AnalysisCommand command) {
        guard.requireAnalysisQuota(command.ownerId());
        return target.run(command);
    }
}

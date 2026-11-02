package com.aquasense.intelligence.service;

import com.aquasense.intelligence.engine.AnalysisEngine;
import com.aquasense.intelligence.engine.AnalysisInput;
import com.aquasense.intelligence.engine.Sample;
import com.aquasense.measurement.domain.Measurement;
import com.aquasense.measurement.service.MeasurementService;
import com.aquasense.device.domain.SourceKind;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.service.WaterProfileService;
import java.util.List;

public class ComputingAnalysis implements AnalysisUseCase {

    static final int MAX_SAMPLES = 1000;

    private final MeasurementService measurements;
    private final WaterProfileService profiles;
    private final AnalysisEngine engine;

    public ComputingAnalysis(MeasurementService measurements, WaterProfileService profiles, AnalysisEngine engine) {
        this.measurements = measurements;
        this.profiles = profiles;
        this.engine = engine;
    }

    @Override
    public AnalysisResult run(AnalysisCommand c) {
        WaterProfile profile = profiles.get(c.profileId(), c.ownerId());
        List<Measurement> data = measurements.forAnalysis(c.ownerId(), c.profileId(), c.from(), c.to(),
                c.includeSimulated(), MAX_SAMPLES);
        List<Sample> samples = data.stream().map(m -> new Sample(m.getMeasuredAt(), m.getPh().doubleValue(),
                m.getSourceKind() == SourceKind.SIMULATED)).toList();
        var report = engine.analyze(new AnalysisInput(profile.getName(), profile.getType(),
                profile.getOptimalMin().doubleValue(), profile.getOptimalMax().doubleValue(),
                profile.getCautionMin().doubleValue(), profile.getCautionMax().doubleValue(), samples));
        return new AnalysisResult(null, null, 0, report);
    }
}

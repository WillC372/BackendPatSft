package com.aquaassistant.measurement;

import com.aquaassistant.profile.WaterProfile;

/**
 * Observer: any module interested in new measurements (alerts, audit) implements
 * this interface and is registered automatically in {@link MeasurementPublisher}.
 */
public interface MeasurementListener {

    void onMeasurementRecorded(Measurement measurement, WaterProfile profile);
}

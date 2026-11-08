package com.aquasense.measurement;

import com.aquasense.profile.WaterProfile;

/**
 * Observer: cualquier modulo interesado en mediciones nuevas (alertas, auditoria) implementa
 * esta interfaz y queda registrado automáticamente en {@link MeasurementPublisher}.
 */
public interface MeasurementListener {

    void onMeasurementRecorded(Measurement measurement, WaterProfile profile);
}

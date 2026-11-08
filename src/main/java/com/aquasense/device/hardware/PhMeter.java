package com.aquasense.device.hardware;

/**
 * Interfaz objetivo del patron Adapter. El resto del sistema solo conoce esta interfaz;
 * cada protocolo de medidor (Atlas EZO, firmware ESP32, simulador) la implementa a su manera.
 */
public interface PhMeter {

    String port();

    MeterProtocol protocol();

    boolean isSimulated();

    void open();

    PhReading read();

    boolean supportsCalibration();

    String calibrate(CalibrationPoint point, double bufferValue);

    void close();
}

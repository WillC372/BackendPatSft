package com.aquasense.device.hardware;

/**
 * Target interface of the Adapter pattern. The rest of the system only knows this interface;
 * every meter protocol (Atlas EZO, ESP32 firmware, simulator) implements it in its own way.
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

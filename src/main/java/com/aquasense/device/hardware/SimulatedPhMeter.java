package com.aquasense.device.hardware;

import java.time.Instant;
import java.util.Random;

/**
 * Medidor simulado para pruebas y demostraciones. Toda lectura que produce queda marcada
 * como SIMULATED en la base de datos y en la interfaz; nunca se presenta como medición real.
 */
public class SimulatedPhMeter implements PhMeter {

    public static final String PORT = "SIMULATOR";

    private final Random random;
    private final double baseline;
    private long tick;

    public SimulatedPhMeter(double baseline, long seed) {
        this.baseline = baseline;
        this.random = new Random(seed);
    }

    @Override
    public String port() {
        return PORT;
    }

    @Override
    public MeterProtocol protocol() {
        return MeterProtocol.SIMULATOR;
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public void open() {
        tick = 0;
    }

    @Override
    public PhReading read() {
        tick++;
        double drift = 0.15 * Math.sin(tick / 30.0);
        double noise = random.nextGaussian() * 0.03;
        double ph = Math.max(0, Math.min(14, baseline + drift + noise));
        double temperature = 24.0 + 0.5 * Math.sin(tick / 60.0) + random.nextGaussian() * 0.05;
        return new PhReading(Math.round(ph * 100) / 100.0, Math.round(temperature * 10) / 10.0, Instant.now());
    }

    @Override
    public boolean supportsCalibration() {
        return false;
    }

    @Override
    public String calibrate(CalibrationPoint point, double bufferValue) {
        throw new DeviceException("El simulador no admite calibración.");
    }

    @Override
    public void close() {
        tick = 0;
    }
}

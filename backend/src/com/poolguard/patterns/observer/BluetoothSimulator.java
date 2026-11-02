package com.poolguard.patterns.observer;

import com.poolguard.model.SensorReading;

import java.time.LocalTime;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OBSERVER PATTERN - Concrete subject.
 *
 * Simulates a Bluetooth water meter broadcasting readings every couple of
 * seconds for live testing of design patterns and scenarios:
 *   - OPTIMAL: normal clean pool conditions
 *   - CAUTION: slight deviations triggering dosage alerts
 *   - CONTAMINATION: sudden organic contamination (urine/fluids) dropping pH and chlorine
 *   - HIGH_BATHERS: peak pool occupancy putting pressure on sanitation
 *   - CUSTOM: user-defined parameters
 */
public class BluetoothSimulator extends SensorSubject implements Runnable {

    private final Random random = new Random();
    private String deviceId;

    private double ph = 7.40;
    private double temperature = 26.0;
    private double chlorine = 1.80;
    private int batherLoad = 8;
    private boolean contaminationEvent = false;
    private long readingId = 0;

    private String scenario = "OPTIMAL";
    private volatile boolean running = false;
    private ScheduledExecutorService executor;

    public BluetoothSimulator(String deviceId) {
        this.deviceId = deviceId;
    }

    /** Starts broadcasting readings at the fixed interval (daemon thread). */
    public synchronized void start(long intervalMs) {
        stop();
        this.running = true;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bluetooth-simulator");
            t.setDaemon(true);
            return t;
        });
        this.executor.scheduleAtFixedRate(this, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** Stops broadcasting readings. */
    public synchronized void stop() {
        this.running = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized String getScenario() {
        return scenario;
    }

    /** Switches the active simulation scenario. */
    public synchronized void setScenario(String newScenario) {
        if (newScenario == null) return;
        this.scenario = newScenario.trim().toUpperCase();
        this.contaminationEvent = false;

        switch (this.scenario) {
            case "OPTIMAL":
                this.ph = 7.38;
                this.temperature = 26.0;
                this.chlorine = 1.90;
                this.batherLoad = 6;
                break;
            case "CAUTION":
                this.ph = 7.72;
                this.temperature = 27.5;
                this.chlorine = 0.82;
                this.batherLoad = 16;
                break;
            case "CONTAMINATION":
                this.ph = 6.65;
                this.chlorine = 0.35;
                this.temperature = 26.8;
                this.batherLoad = 18;
                this.contaminationEvent = true;
                break;
            case "HIGH_BATHERS":
                this.ph = 7.25;
                this.temperature = 28.2;
                this.chlorine = 0.95;
                this.batherLoad = 28;
                break;
            default:
                break;
        }
    }

    /** Injects custom manual values into the simulator. */
    public synchronized void setCustomValues(double customPh, double customTemp, double customChlorine, int customBathers) {
        this.scenario = "CUSTOM";
        this.contaminationEvent = false;
        if (customPh > 0) this.ph = customPh;
        if (customTemp > 0) this.temperature = customTemp;
        if (customChlorine >= 0) this.chlorine = customChlorine;
        if (customBathers >= 0) this.batherLoad = customBathers;
    }

    /** Triggers a sudden organic contamination event (for live testing). */
    public synchronized void triggerContamination() {
        setScenario("CONTAMINATION");
    }

    public synchronized void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public synchronized String getDeviceId() {
        return deviceId;
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }

        // Small realistic physical drift depending on the scenario
        if ("OPTIMAL".equals(scenario)) {
            ph += (7.40 - ph) * 0.05 + random.nextGaussian() * 0.015;
            temperature += (26.0 - temperature) * 0.02 + random.nextGaussian() * 0.03;
            chlorine += (1.90 - chlorine) * 0.04 + random.nextGaussian() * 0.015;
            chlorine = Math.max(0.1, chlorine - batherLoad * 0.0003);
        } else if ("CAUTION".equals(scenario)) {
            ph += (7.72 - ph) * 0.05 + random.nextGaussian() * 0.02;
            temperature += (27.5 - temperature) * 0.02 + random.nextGaussian() * 0.04;
            chlorine += (0.82 - chlorine) * 0.04 + random.nextGaussian() * 0.02;
        } else if ("CONTAMINATION".equals(scenario)) {
            ph -= 0.04 + random.nextDouble() * 0.03;
            chlorine = Math.max(0.1, chlorine * 0.92);
            if (ph < 6.4) {
                ph = 6.4;
            }
        } else if ("HIGH_BATHERS".equals(scenario)) {
            ph += (7.25 - ph) * 0.04 + random.nextGaussian() * 0.02;
            temperature += (28.2 - temperature) * 0.02 + random.nextGaussian() * 0.03;
            chlorine = Math.max(0.2, chlorine - 0.01 - random.nextDouble() * 0.01);
            batherLoad = Math.max(20, batherLoad + random.nextInt(3) - 1);
        } else {
            // Custom mode slight drift
            ph += random.nextGaussian() * 0.01;
            chlorine = Math.max(0.0, chlorine + random.nextGaussian() * 0.01);
            temperature += random.nextGaussian() * 0.02;
        }

        SensorReading reading = new SensorReading(
                ++readingId,
                round2(ph),
                round2(temperature),
                round2(chlorine),
                batherLoad,
                System.currentTimeMillis());

        notifyObservers(new SensorData(reading, deviceId));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

package com.aquasense.device.hardware;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Adapter for the Atlas Scientific EZO-pH circuit in UART mode (9600 baud, commands terminated with CR).
 * Commands used: "C,0" (disables continuous reading), "R" (single reading), "Cal,mid|low|high,value".
 * It must be verified with real hardware: see docs/Hardware.md.
 */
public class AtlasEzoPhAdapter implements PhMeter {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final SerialLink link;

    public AtlasEzoPhAdapter(SerialLink link) {
        this.link = link;
    }

    @Override
    public String port() {
        return link.portName();
    }

    @Override
    public MeterProtocol protocol() {
        return MeterProtocol.ATLAS_EZO;
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public void open() {
        link.open();
        link.writeLine("C,0");
        skipAcknowledgement();
    }

    @Override
    public PhReading read() {
        link.writeLine("R");
        String line = nextDataLine();
        try {
            return new PhReading(Double.parseDouble(line), null, Instant.now());
        } catch (NumberFormatException ex) {
            throw new DeviceException("Respuesta inesperada del EZO-pH: " + line, ex);
        }
    }

    @Override
    public boolean supportsCalibration() {
        return true;
    }

    @Override
    public String calibrate(CalibrationPoint point, double bufferValue) {
        link.writeLine(String.format(Locale.US, "Cal,%s,%.2f", point.name().toLowerCase(Locale.ROOT), bufferValue));
        String response = link.readLine(READ_TIMEOUT);
        if (!"*OK".equals(response)) {
            throw new DeviceException("El EZO-pH no confirmó la calibración (" + response + ").");
        }
        return "Calibración " + point.name() + " registrada en el EZO-pH.";
    }

    @Override
    public void close() {
        link.close();
    }

    private void skipAcknowledgement() {
        try {
            link.readLine(Duration.ofMillis(800));
        } catch (DeviceException ignored) {
            // The EZO may not answer when continuous mode was already off.
        }
    }

    private String nextDataLine() {
        String line = link.readLine(READ_TIMEOUT);
        while (line.startsWith("*")) {
            if (line.startsWith("*ER")) {
                throw new DeviceException("El EZO-pH respondió con error.");
            }
            line = link.readLine(READ_TIMEOUT);
        }
        return line;
    }
}

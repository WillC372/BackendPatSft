package com.aquasense.device.hardware;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Adapter for the AquaSense ESP32 firmware included in hardware/Esp32PhSensor.
 * Text protocol: "READ" answers "PH:7.12;T:24.5"; "CAL:MID:7.00" answers "OK" or "ERR:reason".
 */
public class AquaSenseFirmwareAdapter implements PhMeter {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final SerialLink link;

    public AquaSenseFirmwareAdapter(SerialLink link) {
        this.link = link;
    }

    @Override
    public String port() {
        return link.portName();
    }

    @Override
    public MeterProtocol protocol() {
        return MeterProtocol.AQUASENSE_ESP32;
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public void open() {
        link.open();
        link.writeLine("PING");
        String response = link.readLine(READ_TIMEOUT);
        if (!response.startsWith("PONG")) {
            throw new DeviceException("El dispositivo no respondió como firmware AquaSense (" + response + ").");
        }
    }

    @Override
    public PhReading read() {
        link.writeLine("READ");
        return parse(link.readLine(READ_TIMEOUT));
    }

    static PhReading parse(String line) {
        if (!line.startsWith("PH:")) {
            throw new DeviceException("Respuesta inesperada del firmware: " + line);
        }
        Double ph = null;
        Double temperature = null;
        try {
            for (String part : line.split(";")) {
                String[] pair = part.split(":", 2);
                if (pair.length == 2 && "PH".equals(pair[0])) {
                    ph = Double.parseDouble(pair[1]);
                } else if (pair.length == 2 && "T".equals(pair[0]) && !"NA".equals(pair[1])) {
                    temperature = Double.parseDouble(pair[1]);
                }
            }
        } catch (NumberFormatException ex) {
            throw new DeviceException("Respuesta con números inválidos: " + line, ex);
        }
        if (ph == null) {
            throw new DeviceException("La respuesta no contiene pH: " + line);
        }
        return new PhReading(ph, temperature, Instant.now());
    }

    @Override
    public boolean supportsCalibration() {
        return true;
    }

    @Override
    public String calibrate(CalibrationPoint point, double bufferValue) {
        link.writeLine(String.format(Locale.US, "CAL:%s:%.2f", point.name(), bufferValue));
        String response = link.readLine(READ_TIMEOUT);
        if (!response.startsWith("OK")) {
            throw new DeviceException("El firmware rechazo la calibración: " + response);
        }
        return "Calibración " + point.name() + " guardada en el ESP32.";
    }

    @Override
    public void close() {
        link.close();
    }
}

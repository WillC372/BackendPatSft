package com.aquasense.device.hardware;

import java.time.Duration;
import java.time.Instant;

/**
 * Adapter for laboratory meters that send their reading as a text line (USB or RS-232) when the user presses
 * "Print"/"Send" or at a fixed interval. It sends no commands: it waits for the next line that contains a pH.
 * These meters are calibrated on the instrument itself, so calibration from AquaSense is not supported.
 */
public class TextMeterAdapter implements PhMeter {

    private static final Duration WAIT = Duration.ofSeconds(8);

    private final SerialLink link;

    public TextMeterAdapter(SerialLink link) {
        this.link = link;
    }

    @Override
    public String port() {
        return link.portName();
    }

    @Override
    public MeterProtocol protocol() {
        return MeterProtocol.TEXT_METER;
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public void open() {
        link.open();
    }

    @Override
    public PhReading read() {
        Instant deadline = Instant.now().plus(WAIT);
        String last = "";
        while (Instant.now().isBefore(deadline)) {
            last = link.readLine(Duration.between(Instant.now(), deadline));
            var reading = TextMeterParser.parse(last);
            if (reading.isPresent()) {
                return reading.get();
            }
        }
        throw new DeviceException("El medidor no envió un valor de pH" + (last.isBlank() ? "" : " (última línea: " + last + ")")
            + ". Pulsa el botón de enviar o imprimir del medidor, o revisa su velocidad (baudios).");
    }

    @Override
    public boolean supportsCalibration() {
        return false;
    }

    @Override
    public String calibrate(CalibrationPoint point, double bufferValue) {
        throw new DeviceException("Este medidor se calibra desde sus propios botones.");
    }

    @Override
    public void close() {
        link.close();
    }
}

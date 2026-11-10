package com.aquasense.device.hardware;

import java.util.Arrays;
import java.util.List;
import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Component;

/**
 * Factory Method: creates the right adapter for the protocol chosen by the user.
 * Services ask for a {@link PhMeter} without knowing the concrete classes or the transport.
 */
@Component
public class PhMeterFactory {

    private static final double SIMULATOR_BASELINE = 7.15;

    public PhMeter create(MeterProtocol protocol, String port) {
        return switch (protocol) {
            case SIMULATOR -> new SimulatedPhMeter(SIMULATOR_BASELINE, System.nanoTime());
            case ATLAS_EZO -> new AtlasEzoPhAdapter(createLink(port, "\r"));
            case AQUASENSE_ESP32 -> new AquaSenseFirmwareAdapter(createLink(port, "\n"));
            case TEXT_METER -> new TextMeterAdapter(createLink(port, "\r\n"));
        };
    }

    protected SerialLink createLink(String port, String terminator) {
        if (port == null || port.isBlank() || SimulatedPhMeter.PORT.equals(port)) {
            throw new DeviceException("Selecciona un puerto serie válido.");
        }
        return new JSerialCommLink(port, terminator);
    }

    public List<DetectedPort> detectPorts() {
        try {
            return Arrays.stream(SerialPort.getCommPorts())
                .map(p -> new DetectedPort(p.getSystemPortName(), p.getDescriptivePortName(), p.getPortDescription(),
                    isBluetooth(p.getDescriptivePortName() + " " + p.getPortDescription() + " " + p.getSystemPortName())))
                .toList();
        } catch (RuntimeException | LinkageError ex) {
            return List.of();
        }
    }

    private static boolean isBluetooth(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("bluetooth") || lower.contains("rfcomm");
    }

    public record DetectedPort(String systemName, String descriptiveName, String description, boolean bluetooth) {
    }
}

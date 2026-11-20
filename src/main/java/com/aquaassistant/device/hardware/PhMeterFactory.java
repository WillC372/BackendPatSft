package com.aquaassistant.device.hardware;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Component;

/**
 * Factory Method: creates the right meter for the protocol chosen by the user. Services ask for a {@link PhMeter}
 * without knowing the concrete classes or the transport. Each real protocol is built by its family
 * ({@link MeterKit}, Abstract Factory), which creates the link and the adapter that belong together.
 */
@Component
public class PhMeterFactory {

    private static final double SIMULATOR_BASELINE = 7.15;

    private final Map<MeterProtocol, MeterKit> kits = new EnumMap<>(MeterProtocol.class);

    public PhMeterFactory() {
        for (MeterKit kit : List.of(new AtlasEzoKit(), new Esp32FirmwareKit(), new TextMeterKit())) {
            kits.put(kit.protocol(), kit);
        }
    }

    public PhMeter create(MeterProtocol protocol, String port) {
        if (protocol == MeterProtocol.SIMULATOR) {
            return new SimulatedPhMeter(SIMULATOR_BASELINE, System.nanoTime());
        }
        MeterKit kit = kitFor(protocol);
        return kit.createMeter(kit.createLink(port));
    }

    /** The family that builds the meters of this protocol. */
    public MeterKit kitFor(MeterProtocol protocol) {
        MeterKit kit = kits.get(protocol);
        if (kit == null) {
            throw new DeviceException("Tipo de medidor no soportado: " + protocol);
        }
        return kit;
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

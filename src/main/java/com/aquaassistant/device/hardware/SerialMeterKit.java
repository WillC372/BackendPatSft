package com.aquaassistant.device.hardware;

/**
 * Common part of the meter families that talk over a serial port (USB, or Bluetooth paired as a serial port).
 * Each concrete kit only says its line terminator and which adapter it creates ({@link #createMeter} is the
 * Factory Method each family implements).
 */
public abstract class SerialMeterKit implements MeterKit {

    /** The characters that end every command this model expects. */
    protected abstract String terminator();

    @Override
    public SerialLink createLink(String port) {
        if (port == null || port.isBlank() || SimulatedPhMeter.PORT.equals(port)) {
            throw new DeviceException("Selecciona un puerto serie válido.");
        }
        return new JSerialCommLink(port, terminator());
    }
}

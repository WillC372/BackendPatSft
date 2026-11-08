package com.aquasense.device.hardware;

import java.time.Duration;

/**
 * Text transport over a serial port. It separates the protocol (adapters) from the physical channel,
 * which allows testing the adapters with a fake link and no hardware.
 */
public interface SerialLink {

    void open();

    void writeLine(String line);

    String readLine(Duration timeout);

    void close();

    String portName();
}

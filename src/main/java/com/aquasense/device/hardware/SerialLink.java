package com.aquasense.device.hardware;

import java.time.Duration;

/**
 * Transporte de texto por puerto serie. Separa el protocolo (adaptadores) del canal físico,
 * lo que permite probar los adaptadores con un enlace falso sin hardware.
 */
public interface SerialLink {

    void open();

    void writeLine(String line);

    String readLine(Duration timeout);

    void close();

    String portName();
}

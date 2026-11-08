package com.aquasense.support;

import com.aquasense.device.hardware.DeviceException;
import com.aquasense.device.hardware.SerialLink;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Enlace serie falso para pruebas: devuelve respuestas programadas y registra los comandos enviados.
 * Es una herramienta de prueba, no simula un medidor ante el usuario.
 */
public class FakeSerialLink implements SerialLink {

    private final Deque<String> responses = new ArrayDeque<>();
    private final List<String> sent = new ArrayList<>();
    private boolean open;

    public FakeSerialLink respond(String... lines) {
        responses.addAll(List.of(lines));
        return this;
    }

    public List<String> sent() {
        return sent;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void open() {
        open = true;
    }

    @Override
    public void writeLine(String line) {
        sent.add(line);
    }

    @Override
    public String readLine(Duration timeout) {
        if (responses.isEmpty()) {
            throw new DeviceException("El medidor no respondió a tiempo.");
        }
        return responses.pollFirst();
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public String portName() {
        return "/dev/ttyFAKE0";
    }
}

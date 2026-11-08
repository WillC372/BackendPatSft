package com.aquasense.support;

import com.aquasense.device.hardware.DeviceException;
import com.aquasense.device.hardware.SerialLink;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Fake serial link for tests: returns scripted responses and records the commands sent.
 * It is a test tool; it does not simulate a meter for the user.
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

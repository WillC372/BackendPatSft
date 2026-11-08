package com.aquasense.device.hardware;

import com.fazecast.jSerialComm.SerialPort;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Real serial link with jSerialComm. It works for USB serial and for classic Bluetooth (SPP profile),
 * which the operating system exposes as a serial port (COMx on Windows, /dev/rfcommX on Linux).
 */
public class JSerialCommLink implements SerialLink {

    private static final int BAUD_RATE = 9600;

    private final String portName;
    private final String terminator;
    private SerialPort port;

    public JSerialCommLink(String portName, String terminator) {
        this.portName = portName;
        this.terminator = terminator;
    }

    @Override
    public void open() {
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 200);
        if (!port.openPort()) {
            throw new DeviceException("No se pudo abrir el puerto " + portName + ". Verifica que esté conectado y libre.");
        }
    }

    @Override
    public void writeLine(String line) {
        byte[] bytes = (line + terminator).getBytes(StandardCharsets.US_ASCII);
        if (port == null || port.writeBytes(bytes, bytes.length) != bytes.length) {
            throw new DeviceException("No se pudo enviar el comando al medidor.");
        }
    }

    @Override
    public String readLine(Duration timeout) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        byte[] one = new byte[1];
        while (System.currentTimeMillis() < deadline) {
            int read = port.readBytes(one, 1);
            if (read == 1) {
                if (one[0] == '\r' || one[0] == '\n') {
                    if (buffer.size() > 0) {
                        return buffer.toString(StandardCharsets.US_ASCII).trim();
                    }
                } else {
                    buffer.write(one[0]);
                }
            } else if (read < 0) {
                throw new DeviceException("Se perdió la comunicación con el medidor.");
            }
        }
        throw new DeviceException("El medidor no respondió a tiempo.");
    }

    @Override
    public void close() {
        if (port != null) {
            port.closePort();
        }
    }

    @Override
    public String portName() {
        return portName;
    }
}

package com.aquasense.device.hardware;

public class DeviceException extends RuntimeException {

    public DeviceException(String message) {
        super(message);
    }

    public DeviceException(String message, Throwable cause) {
        super(message, cause);
    }
}

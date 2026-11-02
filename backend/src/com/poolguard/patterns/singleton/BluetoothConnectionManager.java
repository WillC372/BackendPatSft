package com.poolguard.patterns.singleton;

/**
 * SINGLETON PATTERN
 *
 * Unique manager of the Bluetooth connection to the physical water meter.
 * Guarantees that the whole application shares one connection state
 * (DISCONNECTED / CONNECTED), mode (NONE / PHYSICAL / SIMULATOR), and device name.
 */
public final class BluetoothConnectionManager {

    public enum ConnectionMode {
        NONE,
        PHYSICAL,
        SIMULATOR
    }

    private static BluetoothConnectionManager instance;

    private String deviceName;
    private String status = "DISCONNECTED";
    private ConnectionMode mode = ConnectionMode.NONE;

    private BluetoothConnectionManager() {
    }

    /** Returns the unique instance of the connection manager (thread-safe). */
    public static synchronized BluetoothConnectionManager getInstance() {
        if (instance == null) {
            instance = new BluetoothConnectionManager();
        }
        return instance;
    }

    /** Connects to a physical Bluetooth device. */
    public synchronized String connectPhysical(String deviceName) {
        this.deviceName = deviceName;
        this.status = "CONNECTED";
        this.mode = ConnectionMode.PHYSICAL;
        return status;
    }

    /** Connects using the integrated sensor simulator. */
    public synchronized String connectSimulator(String deviceName) {
        this.deviceName = deviceName;
        this.status = "CONNECTED";
        this.mode = ConnectionMode.SIMULATOR;
        return status;
    }

    /** Backward compatibility helper for connect. */
    public synchronized String connect(String deviceName) {
        return connectSimulator(deviceName);
    }

    /** Disconnects from the current device. */
    public synchronized String disconnect() {
        this.deviceName = null;
        this.status = "DISCONNECTED";
        this.mode = ConnectionMode.NONE;
        return status;
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized String getDeviceName() {
        return deviceName;
    }

    public synchronized ConnectionMode getMode() {
        return mode;
    }

    public synchronized boolean isConnected() {
        return "CONNECTED".equals(status);
    }
}

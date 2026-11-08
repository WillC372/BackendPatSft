package com.aquasense.device.command;

import com.aquasense.device.connection.DeviceConnection;

/**
 * Command: every action on the meter is an object with its own name and execution.
 * The invoker runs them uniformly and records them in the operation history.
 */
public interface DeviceCommand<T> {

    String name();

    String describe();

    T execute(DeviceConnection connection);
}

package com.aquasense.device.command;

import com.aquasense.device.connection.DeviceConnection;

/**
 * Command: cada acción sobre el medidor es un objeto con nombre y ejecucion propia.
 * El invocador las ejecuta de forma uniforme y las registra en el historial de operaciones.
 */
public interface DeviceCommand<T> {

    String name();

    String describe();

    T execute(DeviceConnection connection);
}

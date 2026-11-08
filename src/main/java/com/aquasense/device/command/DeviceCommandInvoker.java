package com.aquasense.device.command;

import com.aquasense.audit.AuditService;
import com.aquasense.device.connection.DeviceConnection;
import org.springframework.stereotype.Component;

@Component
public class DeviceCommandInvoker {

    private final DeviceConnection connection;
    private final AuditService auditService;

    public DeviceCommandInvoker(DeviceConnection connection, AuditService auditService) {
        this.connection = connection;
        this.auditService = auditService;
    }

    public <T> T run(Long userId, DeviceCommand<T> command) {
        try {
            T result = command.execute(connection);
            auditService.record(userId, command.name(), command.describe());
            return result;
        } catch (RuntimeException ex) {
            auditService.recordIndependently(userId, command.name() + "_FAILED", command.describe() + ": " + ex.getMessage());
            throw ex;
        }
    }
}

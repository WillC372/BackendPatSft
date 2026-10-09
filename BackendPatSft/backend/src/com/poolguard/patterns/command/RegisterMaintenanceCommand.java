package com.poolguard.patterns.command;

import com.poolguard.model.MaintenanceRecord;
import com.poolguard.patterns.mediator.Event;
import com.poolguard.patterns.mediator.EventBus;
import com.poolguard.patterns.mediator.EventType;
import com.poolguard.patterns.repository.MaintenanceRepository;
import com.poolguard.patterns.repository.UnitOfWork;

/**
 * COMMAND PATTERN
 *
 * Registers a chemical maintenance action on the pool. Persisted through
 * the repository + Unit of Work and broadcast on the Event Bus.
 */
public class RegisterMaintenanceCommand implements Command {

    private final long poolId;
    private final String username;
    private final String description;
    private final String chemicalUsed;
    private final double quantity;
    private final String unit;
    private final MaintenanceRepository maintenanceRepository;
    private final UnitOfWork unitOfWork;
    private final EventBus eventBus;

    public RegisterMaintenanceCommand(long poolId, String username, String description,
                                      String chemicalUsed, double quantity, String unit,
                                      MaintenanceRepository maintenanceRepository,
                                      UnitOfWork unitOfWork, EventBus eventBus) {
        this.poolId = poolId;
        this.username = username;
        this.description = description;
        this.chemicalUsed = chemicalUsed;
        this.quantity = quantity;
        this.unit = unit;
        this.maintenanceRepository = maintenanceRepository;
        this.unitOfWork = unitOfWork;
        this.eventBus = eventBus;
    }

    @Override
    public void execute() {
        MaintenanceRecord record = new MaintenanceRecord(
                0, poolId, username, description, chemicalUsed, quantity, unit, System.currentTimeMillis());
        maintenanceRepository.save(record);
        unitOfWork.commit();
        eventBus.publish(new Event(EventType.MAINTENANCE_REGISTERED)
                .with("username", username)
                .with("description", description));
    }

    @Override
    public String getDescription() {
        return "Register maintenance '" + description + "' by " + username;
    }
}

package com.poolguard.patterns.command;

import com.poolguard.model.Thresholds;
import com.poolguard.patterns.mediator.Event;
import com.poolguard.patterns.mediator.EventBus;
import com.poolguard.patterns.mediator.EventType;
import com.poolguard.patterns.repository.UnitOfWork;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * COMMAND PATTERN
 *
 * Modifies the safety thresholds that drive the State pattern. The change
 * is persisted immediately (reactive persistence) and broadcast through
 * the Event Bus.
 */
public class UpdateThresholdCommand implements Command {

    private final Thresholds thresholds;
    private final Thresholds newValues;
    private final String username;
    private final UnitOfWork unitOfWork;
    private final EventBus eventBus;

    public UpdateThresholdCommand(Thresholds thresholds, Thresholds newValues, String username,
                                  UnitOfWork unitOfWork, EventBus eventBus) {
        this.thresholds = thresholds;
        this.newValues = newValues;
        this.username = username;
        this.unitOfWork = unitOfWork;
        this.eventBus = eventBus;
    }

    @Override
    public void execute() {
        thresholds.apply(newValues);
        // Persist the thresholds table right away
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(thresholds.toMap());
        unitOfWork.getStore().saveTable("thresholds", rows);
        unitOfWork.commit();
        eventBus.publish(new Event(EventType.THRESHOLD_UPDATED)
                .with("username", username)
                .with("thresholds", thresholds.toMap()));
    }

    @Override
    public String getDescription() {
        return "Update thresholds by " + username;
    }
}

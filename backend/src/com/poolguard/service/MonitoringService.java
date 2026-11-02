package com.poolguard.service;

import com.poolguard.ai.DosageAssistant;
import com.poolguard.ai.PredictiveEngine;
import com.poolguard.analytics.AnalyticsEngine;
import com.poolguard.model.AlertRecord;
import com.poolguard.model.SensorReading;
import com.poolguard.patterns.factory.Alert;
import com.poolguard.patterns.factory.AlertFactory;
import com.poolguard.patterns.mediator.Event;
import com.poolguard.patterns.mediator.EventBus;
import com.poolguard.patterns.mediator.EventType;
import com.poolguard.patterns.observer.SensorData;
import com.poolguard.patterns.observer.SensorObserver;
import com.poolguard.patterns.repository.AlertRepository;
import com.poolguard.patterns.repository.SensorReadingRepository;
import com.poolguard.patterns.repository.UnitOfWork;
import com.poolguard.patterns.state.WaterStateMachine;

/**
 * Central monitoring service.
 *
 * Wires together the patterns:
 *  - OBSERVER: receives sensor packets from the Bluetooth simulator
 *  - STATE:    feeds the WaterStateMachine and reacts to transitions
 *  - MEDIATOR: publishes STATE_CHANGED / PREDICTION_UPDATED events
 *  - FACTORY:  creates the alerts (PUSH / VOICE / VISUAL) dynamically
 *  - REPOSITORY + UNIT OF WORK: persists readings and alerts
 */
public class MonitoringService implements SensorObserver {

    private final WaterStateMachine stateMachine;
    private final AnalyticsEngine analytics;
    private final PredictiveEngine predictiveEngine;
    private final AlertFactory alertFactory;
    private final EventBus eventBus;
    private final UnitOfWork unitOfWork;
    private final SensorReadingRepository readingRepository;
    private final AlertRepository alertRepository;

    private volatile SensorReading latestReading;

    public MonitoringService(WaterStateMachine stateMachine,
                             AnalyticsEngine analytics,
                             PredictiveEngine predictiveEngine,
                             AlertFactory alertFactory,
                             EventBus eventBus,
                             UnitOfWork unitOfWork,
                             SensorReadingRepository readingRepository,
                             AlertRepository alertRepository) {
        this.stateMachine = stateMachine;
        this.analytics = analytics;
        this.predictiveEngine = predictiveEngine;
        this.alertFactory = alertFactory;
        this.eventBus = eventBus;
        this.unitOfWork = unitOfWork;
        this.readingRepository = readingRepository;
        this.alertRepository = alertRepository;
    }

    /** OBSERVER callback: invoked on every new sensor packet. */
    @Override
    public void onSensorData(SensorData data) {
        SensorReading reading = data.getReading();
        latestReading = reading;
        readingRepository.save(reading);
        analytics.addReading(reading);

        // STATE PATTERN: feed the machine and detect transitions
        String previousState = stateMachine.getState().getName();
        stateMachine.update(reading);
        String currentState = stateMachine.getState().getName();

        if (!previousState.equals(currentState)) {
            eventBus.publish(new Event(EventType.STATE_CHANGED)
                    .with("from", previousState)
                    .with("to", currentState)
                    .with("reading", reading.toMap()));
            fireAlertForState(currentState, reading);
        }

        // Analytical engine: warn about anomalous pH drops before they turn critical
        if (analytics.isAnomalousPhDrop()) {
            fireAlert("PUSH", "WARNING",
                    "Caída anómala de pH detectada (tendencia: "
                            + String.format("%.2f", analytics.phSlopePerHour()) + " pH/h).");
        }

        // Predictive engine: early saturation warning
        PredictiveEngine.Prediction prediction =
                predictiveEngine.evaluate(reading, reading.getBatherLoad());
        eventBus.publish(new Event(EventType.PREDICTION_UPDATED)
                .with("prediction", prediction));

        // UNIT OF WORK: atomic commit of everything produced by this reading
        unitOfWork.commit();
    }

    /** Fires the alerts associated with the entered state. */
    private void fireAlertForState(String state, SensorReading r) {
        switch (state) {
            case "CAUTION":
                fireAlert("VISUAL", "WARNING",
                        "Precaución: parámetros del agua fuera del rango óptimo.");
                break;
            case "CRITICAL":
                fireAlert("PUSH", "CRITICAL",
                        "¡AGUA CRÍTICA! Cambie o trate el agua de inmediato.");
                fireAlert("VOICE", "CRITICAL",
                        "Alerta crítica. El agua de la piscina debe ser tratada de inmediato.");
                fireAlert("VISUAL", "CRITICAL",
                        "Estado crítico: pH " + r.getPh() + ", cloro " + r.getChlorinePpm() + " ppm.");
                break;
            default:
                fireAlert("VISUAL", "INFO", "El agua volvió a condiciones óptimas.");
                break;
        }
    }

    /** FACTORY METHOD: creates the alert dynamically and fires it. */
    private void fireAlert(String type, String severity, String messageEs) {
        Alert alert = alertFactory.createAlert(AlertFactory.AlertType.valueOf(type));
        alert.trigger(messageEs, severity);
        alertRepository.save(new AlertRecord(0, type, severity, messageEs, System.currentTimeMillis()));
    }

    public SensorReading getLatestReading() {
        return latestReading;
    }

    public void clearLatestReading() {
        this.latestReading = null;
    }

    public WaterStateMachine getStateMachine() {
        return stateMachine;
    }

    public AnalyticsEngine getAnalytics() {
        return analytics;
    }
}

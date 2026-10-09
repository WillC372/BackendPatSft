package com.poolguard;

import com.poolguard.ai.DosageAssistant;
import com.poolguard.ai.PredictiveEngine;
import com.poolguard.analytics.AnalyticsEngine;
import com.poolguard.api.ApiServer;
import com.poolguard.auth.AuthService;
import com.poolguard.auth.PasswordHasher;
import com.poolguard.auth.Role;
import com.poolguard.auth.SessionManager;
import com.poolguard.auth.User;
import com.poolguard.db.DataStore;
import com.poolguard.model.Pool;
import com.poolguard.model.Thresholds;
import com.poolguard.patterns.command.AuditService;
import com.poolguard.patterns.command.CommandInvoker;
import com.poolguard.patterns.factory.AlertFactory;
import com.poolguard.patterns.factory.DatabaseConnectionFactory;
import com.poolguard.patterns.mediator.EventBus;
import com.poolguard.patterns.observer.BluetoothSimulator;
import com.poolguard.patterns.repository.AlertRepository;
import com.poolguard.patterns.repository.AuditRepository;
import com.poolguard.patterns.repository.MaintenanceRepository;
import com.poolguard.patterns.repository.SensorReadingRepository;
import com.poolguard.patterns.repository.UnitOfWork;
import com.poolguard.patterns.repository.UserRepository;
import com.poolguard.patterns.singleton.BluetoothConnectionManager;
import com.poolguard.patterns.state.WaterStateMachine;
import com.poolguard.report.ReportGenerator;
import com.poolguard.service.MonitoringService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PoolGuard - Smart water quality monitoring for mixed-use pools.
 *
 * Entry point: wires together every design pattern of the case study:
 *
 *  1. State        -> WaterStateMachine + Optimal/Caution/Critical states
 *  2. Observer     -> BluetoothSimulator (subject) + MonitoringService (observer)
 *  3. Mediator     -> EventBus decoupling Bluetooth / AI / UI
 *  4. Factory      -> AlertFactory (PUSH/VOICE/VISUAL) + DatabaseConnectionFactory
 *  5. Singleton    -> BluetoothConnectionManager + SessionManager
 *  6. Command      -> UpdateThreshold / RegisterMaintenance + AuditService
 *  7. Repository   -> repositories + UnitOfWork over the embedded DataStore
 *
 * Usage:  java com.poolguard.Main [port]
 * Then open http://localhost:<port>/
 */
public class Main {

    /** Locates the frontend folder whether running from the project root or from backend/. */
    private static String detectFrontendDir() {
        if (new java.io.File("frontend/index.html").exists()) {
            return "frontend";
        }
        if (new java.io.File("../frontend/index.html").exists()) {
            return "../frontend";
        }
        return "frontend";
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String frontendDir = args.length > 1 ? args[1] : detectFrontendDir();

        // ------------------------------------------------------------------
        // Database layer: Abstract Factory + Repository + Unit of Work
        // ------------------------------------------------------------------
        DatabaseConnectionFactory dbFactory = new DatabaseConnectionFactory();
        DataStore dataStore = dbFactory.createDataStore(DatabaseConnectionFactory.DbEngine.JSON_EMBEDDED);
        UnitOfWork unitOfWork = new UnitOfWork(dataStore);

        UserRepository userRepository = new UserRepository(dataStore);
        SensorReadingRepository readingRepository = new SensorReadingRepository(dataStore);
        AlertRepository alertRepository = new AlertRepository(dataStore);
        MaintenanceRepository maintenanceRepository = new MaintenanceRepository(dataStore);
        AuditRepository auditRepository = new AuditRepository(dataStore);

        // Seed default users (passwords ALWAYS stored hashed with PBKDF2)
        if (userRepository.findAll().isEmpty()) {
            userRepository.save(new User(0, "admin", PasswordHasher.hash("admin123"),
                    Role.ADMIN, "Administrador General"));
            userRepository.save(new User(0, "operario", PasswordHasher.hash("operario123"),
                    Role.OPERATOR, "Operario de Piscina"));
            unitOfWork.commit();
            System.out.println("[DB] Default users seeded (admin/admin123, operario/operario123)");
        }

        // Load or create the pool and the thresholds
        Pool pool;
        List<Map<String, Object>> poolRows = dataStore.loadTable("pools");
        if (poolRows.isEmpty()) {
            pool = new Pool(1, "Piscina Mixta Principal", 250.0);
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(pool.toMap());
            dataStore.saveTable("pools", rows);
        } else {
            pool = Pool.fromMap(poolRows.get(0));
        }

        Thresholds thresholds;
        List<Map<String, Object>> thresholdRows = dataStore.loadTable("thresholds");
        if (thresholdRows.isEmpty()) {
            thresholds = new Thresholds();
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(thresholds.toMap());
            dataStore.saveTable("thresholds", rows);
        } else {
            thresholds = Thresholds.fromMap(thresholdRows.get(0));
        }
        unitOfWork.commit();

        // ------------------------------------------------------------------
        // Patterns wiring
        // ------------------------------------------------------------------
        EventBus eventBus = new EventBus();
        AlertFactory alertFactory = new AlertFactory(eventBus);
        AnalyticsEngine analytics = new AnalyticsEngine();
        PredictiveEngine predictiveEngine = new PredictiveEngine(analytics);
        DosageAssistant dosageAssistant = new DosageAssistant(pool.getVolumeM3());
        WaterStateMachine stateMachine = new WaterStateMachine(thresholds);
        CommandInvoker commandInvoker = new CommandInvoker();
        AuditService auditService = new AuditService(auditRepository, unitOfWork);
        ReportGenerator reportGenerator = new ReportGenerator();

        MonitoringService monitoringService = new MonitoringService(
                stateMachine, analytics, predictiveEngine, alertFactory,
                eventBus, unitOfWork, readingRepository, alertRepository);

        // SINGLETON: unique Bluetooth connection manager (starts DISCONNECTED until user connects or simulates)
        BluetoothConnectionManager btManager = BluetoothConnectionManager.getInstance();
        btManager.disconnect();

        // OBSERVER: the simulator is the subject, the service the observer
        // Attached for Observer pattern, but idle until user starts simulation
        BluetoothSimulator simulator = new BluetoothSimulator("AquaMeter-BT-01 (Simulador)");
        simulator.attach(monitoringService);

        // SINGLETON: session manager
        SessionManager sessionManager = SessionManager.getInstance();

        AuthService authService = new AuthService(userRepository);

        ApiServer apiServer = new ApiServer(
                port, frontendDir,
                authService, sessionManager, monitoringService, stateMachine,
                analytics, predictiveEngine, dosageAssistant, commandInvoker,
                auditService, alertRepository, auditRepository,
                readingRepository, maintenanceRepository, userRepository,
                unitOfWork, thresholds, pool, eventBus, btManager, simulator,
                reportGenerator);
        apiServer.start();

        System.out.println("============================================================");
        System.out.println(" PoolGuard backend running on http://localhost:" + port);
        System.out.println(" Open the URL above in your browser (Spanish UI).");
        System.out.println(" Default users: admin/admin123 (ADMIN), operario/operario123 (OPERATOR)");
        System.out.println("============================================================");
    }
}

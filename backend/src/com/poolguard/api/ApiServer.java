package com.poolguard.api;

import com.poolguard.ai.DosageAssistant;
import com.poolguard.ai.PredictiveEngine;
import com.poolguard.analytics.AnalyticsEngine;
import com.poolguard.auth.AuthService;
import com.poolguard.auth.PasswordHasher;
import com.poolguard.auth.Role;
import com.poolguard.auth.SessionManager;
import com.poolguard.auth.User;
import com.poolguard.db.Json;
import com.poolguard.model.AlertRecord;
import com.poolguard.model.AuditEntry;
import com.poolguard.model.MaintenanceRecord;
import com.poolguard.model.Pool;
import com.poolguard.model.SensorReading;
import com.poolguard.model.Thresholds;
import com.poolguard.patterns.command.AuditService;
import com.poolguard.patterns.command.CommandInvoker;
import com.poolguard.patterns.command.RegisterMaintenanceCommand;
import com.poolguard.patterns.command.UpdateThresholdCommand;
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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Own REST API (no third-party frameworks) built on the JDK HttpServer.
 *
 * It also serves the static frontend (frontend/index.html) so the whole
 * application is available at http://localhost:<port>/.
 *
 * Authentication: Bearer token issued by the SessionManager singleton.
 */
public class ApiServer {

    private final HttpServer server;
    private final String frontendDir;

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final MonitoringService monitoringService;
    private final WaterStateMachine stateMachine;
    private final AnalyticsEngine analytics;
    private final PredictiveEngine predictiveEngine;
    private final DosageAssistant dosageAssistant;
    private final CommandInvoker commandInvoker;
    private final AuditService auditService;
    private final AlertRepository alertRepository;
    private final AuditRepository auditRepository;
    private final SensorReadingRepository readingRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final UnitOfWork unitOfWork;
    private final Thresholds thresholds;
    private final Pool pool;
    private final EventBus eventBus;
    private final BluetoothConnectionManager btManager;
    private final BluetoothSimulator simulator;
    private final ReportGenerator reportGenerator;

    public ApiServer(int port, String frontendDir,
                     AuthService authService,
                     SessionManager sessionManager,
                     MonitoringService monitoringService,
                     WaterStateMachine stateMachine,
                     AnalyticsEngine analytics,
                     PredictiveEngine predictiveEngine,
                     DosageAssistant dosageAssistant,
                     CommandInvoker commandInvoker,
                     AuditService auditService,
                     AlertRepository alertRepository,
                     AuditRepository auditRepository,
                     SensorReadingRepository readingRepository,
                     MaintenanceRepository maintenanceRepository,
                     UserRepository userRepository,
                     UnitOfWork unitOfWork,
                     Thresholds thresholds,
                     Pool pool,
                     EventBus eventBus,
                     BluetoothConnectionManager btManager,
                     BluetoothSimulator simulator,
                     ReportGenerator reportGenerator) throws IOException {
        this.frontendDir = frontendDir;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.monitoringService = monitoringService;
        this.stateMachine = stateMachine;
        this.analytics = analytics;
        this.predictiveEngine = predictiveEngine;
        this.dosageAssistant = dosageAssistant;
        this.commandInvoker = commandInvoker;
        this.auditService = auditService;
        this.alertRepository = alertRepository;
        this.auditRepository = auditRepository;
        this.readingRepository = readingRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.userRepository = userRepository;
        this.unitOfWork = unitOfWork;
        this.thresholds = thresholds;
        this.pool = pool;
        this.eventBus = eventBus;
        this.btManager = btManager;
        this.simulator = simulator;
        this.reportGenerator = reportGenerator;

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        registerRoutes();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void registerRoutes() {
        server.createContext("/api/login", this::handleLogin);
        server.createContext("/api/register", this::handleRegister);
        server.createContext("/api/logout", this::handleLogout);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/readings", this::handleReadings);
        server.createContext("/api/prediction", this::handlePrediction);
        server.createContext("/api/dosage", this::handleDosage);
        server.createContext("/api/alerts", this::handleAlerts);
        server.createContext("/api/thresholds", this::handleThresholds);
        server.createContext("/api/maintenance", this::handleMaintenance);
        server.createContext("/api/audit", this::handleAudit);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/simulate/contamination", this::handleSimulateContamination);
        server.createContext("/api/users", this::handleUsers);
        server.createContext("/api/bluetooth/status", this::handleBluetoothStatus);
        server.createContext("/api/bluetooth/scan", this::handleBluetoothScan);
        server.createContext("/api/bluetooth/connect", this::handleBluetoothConnect);
        server.createContext("/api/bluetooth/disconnect", this::handleBluetoothDisconnect);
        server.createContext("/api/bluetooth/simulator/start", this::handleSimulatorStart);
        server.createContext("/api/bluetooth/simulator/stop", this::handleSimulatorStop);
        server.createContext("/api/bluetooth/simulator/scenario", this::handleSimulatorScenario);
        server.createContext("/", this::handleStatic);
    }

    // =====================================================================
    // Route handlers
    // =====================================================================

    private void handleLogin(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        Map<String, Object> body = parseBody(ex);
        String username = String.valueOf(body.getOrDefault("username", ""));
        String password = String.valueOf(body.getOrDefault("password", ""));
        com.poolguard.auth.User user = authService.authenticate(username, password);
        if (user == null) {
            sendJson(ex, 401, errorBody("Credenciales inválidas"));
            return;
        }
        String token = sessionManager.createSession(user);
        auditService.record(user.getUsername(), "LOGIN", "Inicio de sesión exitoso");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("username", user.getUsername());
        u.put("fullName", user.getFullName());
        u.put("role", user.getRole().name());
        resp.put("user", u);
        sendJson(ex, 200, resp);
    }

    /**
     * Public registration. New accounts are created as OPERATOR by default.
     * An ADMIN account can only be created when the request carries a valid
     * ADMIN session token (RBAC).
     */
    private void handleRegister(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        Map<String, Object> body = parseBody(ex);
        String username = String.valueOf(body.getOrDefault("username", "")).trim();
        String fullName = String.valueOf(body.getOrDefault("fullName", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", ""));
        String roleStr = String.valueOf(body.getOrDefault("role", "OPERATOR")).trim().toUpperCase();

        if (username.isEmpty() || password.isEmpty()) {
            sendJson(ex, 400, errorBody("Usuario y contraseña son obligatorios"));
            return;
        }
        if (username.length() < 3 || password.length() < 6) {
            sendJson(ex, 400, errorBody("Usuario mínimo 3 caracteres y contraseña mínima 6"));
            return;
        }
        if (userRepository.findByUsername(username) != null) {
            sendJson(ex, 409, errorBody("El nombre de usuario ya está en uso"));
            return;
        }

        // RBAC: creating an ADMIN requires an authenticated ADMIN session
        Role role = Role.OPERATOR;
        if ("ADMIN".equals(roleStr)) {
            SessionManager.Session admin = sessionManager.getSession(tokenOf(ex));
            if (admin == null || admin.user.getRole() != Role.ADMIN) {
                sendJson(ex, 403, errorBody("Solo un administrador puede crear cuentas de administrador"));
                return;
            }
            role = Role.ADMIN;
        } else if (!"OPERATOR".equals(roleStr)) {
            sendJson(ex, 400, errorBody("Rol inválido"));
            return;
        }

        // Password is ALWAYS stored as a PBKDF2-SHA256 hash, never plain text
        User user = new User(0, username, PasswordHasher.hash(password), role, fullName);
        authService.registerUser(user);
        unitOfWork.commit();
        auditService.record(username, "REGISTER", "Cuenta creada: " + fullName + " (" + role + ")");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Cuenta creada correctamente. Ya puedes iniciar sesión.");
        resp.put("username", username);
        resp.put("role", role.name());
        sendJson(ex, 201, resp);
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        SessionManager.Session session = requireAuth(ex);
        if (session == null) {
            return;
        }
        sessionManager.invalidate(tokenOf(ex));
        auditService.record(session.user.getUsername(), "LOGOUT", "Cierre de sesión");
        sendJson(ex, 200, okBody("Sesión cerrada"));
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAuth(ex) == null) {
            return;
        }
        Map<String, Object> resp = new LinkedHashMap<>();

        SensorReading latest = monitoringService.getLatestReading();
        Map<String, Object> state = new LinkedHashMap<>();
        if (latest == null) {
            state.put("name", "DISCONNECTED");
            state.put("displayNameEs", "Desconectado");
        } else {
            state.put("name", stateMachine.getState().getName());
            state.put("displayNameEs", stateMachine.getState().getDisplayNameEs());
        }
        resp.put("state", state);

        resp.put("latest", latest == null ? null : latest.toMap());
        resp.put("thresholds", thresholds.toMap());
        resp.put("pool", pool.toMap());

        Map<String, Object> bt = new LinkedHashMap<>();
        bt.put("status", btManager.getStatus());
        bt.put("deviceName", btManager.getDeviceName());
        bt.put("mode", btManager.getMode().name());
        bt.put("simulatorRunning", simulator.isRunning());
        bt.put("scenario", simulator.getScenario());
        resp.put("bluetooth", bt);

        sendJson(ex, 200, resp);
    }

    private void handleReadings(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAuth(ex) == null) {
            return;
        }
        Map<String, String> params = queryToMap(ex.getRequestURI().getQuery());
        int hours = params.containsKey("hours") ? Integer.parseInt(params.get("hours")) : 24;
        long cutoff = System.currentTimeMillis() - hours * 3_600_000L;

        List<SensorReading> all = readingRepository.findAll();
        int skip = Math.max(0, all.size() - 1440); // cap chart points
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = skip; i < all.size(); i++) {
            SensorReading r = all.get(i);
            if (r.getTimestamp() >= cutoff) {
                result.add(r.toMap());
            }
        }
        sendJson(ex, 200, result);
    }

    private void handlePrediction(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAuth(ex) == null) {
            return;
        }
        SensorReading latest = monitoringService.getLatestReading();
        if (latest == null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("saturationScore", 0.0);
            resp.put("collapseProbability", 0.0);
            resp.put("estimatedHoursUntilCritical", -1);
            resp.put("recommendationEs", "Esperando conexión del medidor de agua…");
            resp.put("batherLoad", 0);
            sendJson(ex, 200, resp);
            return;
        }
        PredictiveEngine.Prediction p = predictiveEngine.evaluate(latest, latest.getBatherLoad());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("saturationScore", p.getSaturationScore());
        resp.put("collapseProbability", p.getCollapseProbability());
        resp.put("estimatedHoursUntilCritical", p.getEstimatedHoursUntilCritical());
        resp.put("recommendationEs", p.getRecommendationEs());
        resp.put("batherLoad", latest.getBatherLoad());
        sendJson(ex, 200, resp);
    }

    private void handleDosage(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAuth(ex) == null) {
            return;
        }
        SensorReading latest = monitoringService.getLatestReading();
        if (latest == null) {
            sendJson(ex, 200, new ArrayList<>());
            return;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (DosageAssistant.DosingRecommendation rec : dosageAssistant.compute(latest)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chemical", rec.getChemical());
            m.put("quantity", rec.getQuantity());
            m.put("unit", rec.getUnit());
            m.put("purpose", rec.getPurpose());
            result.add(m);
        }
        sendJson(ex, 200, result);
    }

    private void handleAlerts(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAuth(ex) == null) {
            return;
        }
        List<AlertRecord> all = alertRepository.findAll();
        int skip = Math.max(0, all.size() - 50);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = all.size() - 1; i >= skip; i--) {
            result.add(all.get(i).toMap());
        }
        sendJson(ex, 200, result);
    }

    private void handleThresholds(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAdmin(ex);
        if (session == null) {
            return;
        }
        Map<String, Object> body = parseBody(ex);
        Thresholds newValues = new Thresholds();
        newValues.setPhOptimalMin(num(body, "phOptimalMin", thresholds.getPhOptimalMin()));
        newValues.setPhOptimalMax(num(body, "phOptimalMax", thresholds.getPhOptimalMax()));
        newValues.setPhCautionMin(num(body, "phCautionMin", thresholds.getPhCautionMin()));
        newValues.setPhCautionMax(num(body, "phCautionMax", thresholds.getPhCautionMax()));
        newValues.setPhCriticalMin(num(body, "phCriticalMin", thresholds.getPhCriticalMin()));
        newValues.setPhCriticalMax(num(body, "phCriticalMax", thresholds.getPhCriticalMax()));
        newValues.setChlorineOptimalMin(num(body, "chlorineOptimalMin", thresholds.getChlorineOptimalMin()));
        newValues.setChlorineOptimalMax(num(body, "chlorineOptimalMax", thresholds.getChlorineOptimalMax()));
        newValues.setChlorineCautionMin(num(body, "chlorineCautionMin", thresholds.getChlorineCautionMin()));
        newValues.setChlorineCautionMax(num(body, "chlorineCautionMax", thresholds.getChlorineCautionMax()));
        newValues.setChlorineCriticalMin(num(body, "chlorineCriticalMin", thresholds.getChlorineCriticalMin()));
        newValues.setChlorineCriticalMax(num(body, "chlorineCriticalMax", thresholds.getChlorineCriticalMax()));

        // COMMAND PATTERN: auditable, encapsulated action
        commandInvoker.execute(new UpdateThresholdCommand(
                thresholds, newValues, session.user.getUsername(), unitOfWork, eventBus));
        auditService.record(session.user.getUsername(), "UPDATE_THRESHOLDS", newValues.toString());
        sendJson(ex, 200, okBody("Umbrales actualizados correctamente"));
    }

    private void handleMaintenance(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAdmin(ex);
        if (session == null) {
            return;
        }
        Map<String, Object> body = parseBody(ex);
        String description = String.valueOf(body.getOrDefault("description", ""));
        String chemical = String.valueOf(body.getOrDefault("chemical", ""));
        double quantity = num(body, "quantity", 0);
        String unit = String.valueOf(body.getOrDefault("unit", ""));

        commandInvoker.execute(new RegisterMaintenanceCommand(
                pool.getId(), session.user.getUsername(), description, chemical, quantity, unit,
                maintenanceRepository, unitOfWork, eventBus));
        auditService.record(session.user.getUsername(), "REGISTER_MAINTENANCE",
                description + " (" + chemical + " " + quantity + " " + unit + ")");
        sendJson(ex, 200, okBody("Mantenimiento registrado correctamente"));
    }

    private void handleAudit(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAdmin(ex) == null) {
            return;
        }
        List<AuditEntry> all = auditRepository.findAll();
        int skip = Math.max(0, all.size() - 100);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = all.size() - 1; i >= skip; i--) {
            result.add(all.get(i).toMap());
        }
        sendJson(ex, 200, result);
    }

    private void handleReport(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAuth(ex) == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 24 * 3_600_000L;
        List<SensorReading> last24h = new ArrayList<>();
        for (SensorReading r : readingRepository.findAll()) {
            if (r.getTimestamp() >= cutoff) {
                last24h.add(r);
            }
        }
        List<AlertRecord> alerts = alertRepository.findAll();
        List<MaintenanceRecord> maintenances = maintenanceRepository.findAll();
        String report = reportGenerator.generate(pool.getName(), last24h, alerts, maintenances, thresholds);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("filename", "reporte_cumplimiento_poolguard.txt");
        resp.put("report", report);
        sendJson(ex, 200, resp);
    }

    private void handleSimulateContamination(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAuth(ex);
        if (session == null) {
            return;
        }
        simulator.triggerContamination();
        auditService.record(session.user.getUsername(), "SIMULATE_CONTAMINATION",
                "Evento de contaminación simulado por Bluetooth");
        sendJson(ex, 200, okBody("Evento de contaminación simulado"));
    }

    private void handleUsers(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        if (requireAdmin(ex) == null) {
            return;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.poolguard.auth.User u : userRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("fullName", u.getFullName());
            m.put("role", u.getRole().name());
            result.add(m);
        }
        sendJson(ex, 200, result);
    }

    // =====================================================================
    // Bluetooth and Simulator routes
    // =====================================================================

    private void handleBluetoothStatus(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", btManager.getStatus());
        resp.put("deviceName", btManager.getDeviceName());
        resp.put("mode", btManager.getMode().name());
        resp.put("simulatorRunning", simulator.isRunning());
        resp.put("scenario", simulator.getScenario());
        sendJson(ex, 200, resp);
    }

    private void handleBluetoothScan(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (requireAuth(ex) == null) return;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("found", false);
        resp.put("message", "Escaneo Bluetooth BLE completado: No se detectó un medidor físico 'AquaMeter-BT-01' en el rango de radio. Puedes activar el Simulador de Sensores Integrado para pruebas universitarias.");
        resp.put("devices", new ArrayList<>());
        sendJson(ex, 200, resp);
    }

    private void handleBluetoothConnect(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAuth(ex);
        if (session == null) return;

        Map<String, Object> body = parseBody(ex);
        String name = String.valueOf(body.getOrDefault("deviceName", "AquaMeter-BT-01"));
        btManager.connectPhysical(name);
        auditService.record(session.user.getUsername(), "BLUETOOTH_CONNECT", "Conexión con medidor físico: " + name);
        sendJson(ex, 200, okBody("Conectado con medidor físico " + name));
    }

    private void handleBluetoothDisconnect(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAuth(ex);
        if (session == null) return;

        simulator.stop();
        btManager.disconnect();
        monitoringService.clearLatestReading();
        auditService.record(session.user.getUsername(), "BLUETOOTH_DISCONNECT", "Medidor desconectado");
        sendJson(ex, 200, okBody("Medidor desconectado correctamente"));
    }

    private void handleSimulatorStart(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAuth(ex);
        if (session == null) return;

        Map<String, Object> body = parseBody(ex);
        String scenario = String.valueOf(body.getOrDefault("scenario", "OPTIMAL"));
        simulator.setScenario(scenario);
        btManager.connectSimulator("AquaMeter-BT-01 (Simulador)");
        simulator.start(2000);
        auditService.record(session.user.getUsername(), "SIMULATOR_START", "Simulador iniciado en escenario: " + scenario);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Simulador iniciado en escenario: " + scenario);
        resp.put("scenario", scenario);
        resp.put("status", "CONNECTED");
        resp.put("deviceName", "AquaMeter-BT-01 (Simulador)");
        sendJson(ex, 200, resp);
    }

    private void handleSimulatorStop(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAuth(ex);
        if (session == null) return;

        simulator.stop();
        btManager.disconnect();
        monitoringService.clearLatestReading();
        auditService.record(session.user.getUsername(), "SIMULATOR_STOP", "Simulador detenido");
        sendJson(ex, 200, okBody("Simulador de sensor detenido correctamente"));
    }

    private void handleSimulatorScenario(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, errorBody("Método no permitido"));
            return;
        }
        SessionManager.Session session = requireAuth(ex);
        if (session == null) return;

        Map<String, Object> body = parseBody(ex);
        if (body.containsKey("ph") || body.containsKey("chlorine")) {
            double ph = num(body, "ph", 7.4);
            double temp = num(body, "temperature", 26.0);
            double cl = num(body, "chlorine", 1.8);
            int bathers = (int) num(body, "bathers", 6);
            simulator.setCustomValues(ph, temp, cl, bathers);
            auditService.record(session.user.getUsername(), "SIMULATOR_CUSTOM", "Valores manuales: pH=" + ph + ", Cl=" + cl);
        } else {
            String scenario = String.valueOf(body.getOrDefault("scenario", "OPTIMAL"));
            simulator.setScenario(scenario);
            auditService.record(session.user.getUsername(), "SIMULATOR_SCENARIO", "Escenario cambiado a: " + scenario);
        }

        if (!simulator.isRunning()) {
            btManager.connectSimulator("AquaMeter-BT-01 (Simulador)");
            simulator.start(2000);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Escenario de prueba actualizado");
        resp.put("scenario", simulator.getScenario());
        sendJson(ex, 200, resp);
    }

    // =====================================================================
    // Static frontend
    // =====================================================================

    private void handleStatic(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        String path = ex.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        Path base = Paths.get(frontendDir).toAbsolutePath().normalize();
        String rel = path.startsWith("/") ? path.substring(1) : path;
        Path file = base.resolve(rel).normalize();
        if (!file.startsWith(base) || !Files.exists(file) || Files.isDirectory(file)) {
            sendJson(ex, 404, errorBody("Recurso no encontrado"));
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(path));
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Handles CORS preflight requests (OPTIONS). */
    private boolean isOptions(HttpExchange ex) throws IOException {
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private SessionManager.Session requireAuth(HttpExchange ex) throws IOException {
        String token = tokenOf(ex);
        SessionManager.Session session = sessionManager.getSession(token);
        if (session == null) {
            sendJson(ex, 401, errorBody("Autenticación requerida"));
            return null;
        }
        return session;
    }

    private SessionManager.Session requireAdmin(HttpExchange ex) throws IOException {
        SessionManager.Session session = requireAuth(ex);
        if (session == null) {
            return null;
        }
        if (session.user.getRole() != com.poolguard.auth.Role.ADMIN) {
            sendJson(ex, 403, errorBody("Se requiere rol de Administrador"));
            return null;
        }
        return session;
    }

    private String tokenOf(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Map<String, Object> parseBody(HttpExchange ex) throws IOException {
        String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
        if (body.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = Json.parse(body);
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        }
        return new LinkedHashMap<>();
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null) {
            return map;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return map;
    }

    private static double num(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }

    private static Map<String, Object> okBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}

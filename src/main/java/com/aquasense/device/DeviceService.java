package com.aquasense.device;

import com.aquasense.common.BusinessException;
import com.aquasense.config.AppProperties;
import com.aquasense.device.command.CalibrateCommand;
import com.aquasense.device.command.DeviceCommandInvoker;
import com.aquasense.device.command.MeasureCommand;
import com.aquasense.device.command.StartLiveCommand;
import com.aquasense.device.command.StopLiveCommand;
import com.aquasense.device.connection.ConnectionSession;
import com.aquasense.device.connection.DeviceConnection;
import com.aquasense.device.connection.DeviceSnapshot;
import com.aquasense.device.connection.DeviceState;
import com.aquasense.device.dto.CalibrateRequest;
import com.aquasense.device.dto.ConnectRequest;
import com.aquasense.device.dto.DetectedDeviceResponse;
import com.aquasense.device.dto.DeviceStatusResponse;
import com.aquasense.device.dto.ReadingPreviewResponse;
import com.aquasense.device.dto.RegisteredDeviceResponse;
import com.aquasense.device.hardware.MeterProtocol;
import com.aquasense.device.hardware.PhMeter;
import com.aquasense.device.hardware.PhMeterFactory;
import com.aquasense.device.hardware.PhReading;
import com.aquasense.device.hardware.SimulatedPhMeter;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.measurement.dto.NewReading;
import com.aquasense.user.UserSettings;
import com.aquasense.user.UserSettingsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade of the hardware subsystem: controllers use a single class to scan,
 * connect, measure, calibrate and stream, without knowing adapters, states or commands.
 */
@Service
public class DeviceService {

    private static final Duration PENDING_TTL = Duration.ofMinutes(15);

    private final DeviceConnection connection;
    private final PhMeterFactory meterFactory;
    private final DeviceCommandInvoker invoker;
    private final DeviceRepository repository;
    private final MeasurementService measurementService;
    private final UserSettingsRepository settingsRepository;
    private final int measureSamples;
    private final Map<String, PendingReading> pending = new ConcurrentHashMap<>();

    public DeviceService(DeviceConnection connection, PhMeterFactory meterFactory, DeviceCommandInvoker invoker,
                         DeviceRepository repository, MeasurementService measurementService,
                         UserSettingsRepository settingsRepository, AppProperties properties) {
        this.connection = connection;
        this.meterFactory = meterFactory;
        this.invoker = invoker;
        this.repository = repository;
        this.measurementService = measurementService;
        this.settingsRepository = settingsRepository;
        this.measureSamples = properties.device().measureSamples();
    }

    @Transactional(readOnly = true)
    public List<DetectedDeviceResponse> scan(Long userId) {
        Map<String, Long> registered = new ConcurrentHashMap<>();
        repository.findByOwnerIdOrderByLastConnectedAtDesc(userId).forEach(d -> registered.put(d.getPort(), d.getId()));
        List<DetectedDeviceResponse> result = new ArrayList<>();
        result.add(new DetectedDeviceResponse(SimulatedPhMeter.PORT, "Simulador AquaSense",
            "Genera lecturas de prueba. No mide agua real.", "SIMULADO", true, MeterProtocol.SIMULATOR.name(),
            registered.get(SimulatedPhMeter.PORT)));
        meterFactory.detectPorts().forEach(port -> result.add(new DetectedDeviceResponse(port.systemName(),
            port.descriptiveName(), port.description(), port.bluetooth() ? "BLUETOOTH_SPP" : "USB_SERIAL", false,
            MeterProtocol.AQUASENSE_ESP32.name(), registered.get(port.systemName()))));
        return result;
    }

    @Transactional(readOnly = true)
    public List<RegisteredDeviceResponse> registered(Long userId) {
        return repository.findByOwnerIdOrderByLastConnectedAtDesc(userId).stream()
            .map(d -> new RegisteredDeviceResponse(d.getId(), d.getName(), d.getProtocol().name(),
                d.getProtocol().label(), d.getPort(), d.isSimulated(), d.getLastConnectedAt()))
            .toList();
    }

    @Transactional
    public DeviceStatusResponse connect(Long userId, ConnectRequest request) {
        DeviceSnapshot current = connection.snapshot();
        if (current.state() != DeviceState.DISCONNECTED && current.ownerId() != null && !current.ownerId().equals(userId)) {
            throw BusinessException.conflict("El medidor está en uso por otra cuenta.");
        }
        if (current.state() == DeviceState.CONNECTED || current.state() == DeviceState.LIVE) {
            throw BusinessException.conflict("Ya hay un medidor conectado. Desconéctalo primero.");
        }
        String port = request.protocol() == MeterProtocol.SIMULATOR ? SimulatedPhMeter.PORT : request.port();
        if (request.protocol() != MeterProtocol.SIMULATOR && meterFactory.detectPorts().stream()
                .noneMatch(p -> p.systemName().equals(port))) {
            // Only serial ports detected by the system are opened; never a path typed by the user (for example /dev/sda).
            throw BusinessException.badRequest("El puerto no existe o no está disponible. Busca los dispositivos de nuevo.");
        }
        PhMeter meter = meterFactory.create(request.protocol(), port);
        String name = request.name() == null || request.name().isBlank() ? defaultName(request.protocol(), port) : request.name().trim();
        Device device = repository.findByOwnerIdAndPort(userId, port)
            .orElseGet(() -> repository.save(new Device(userId, name, request.protocol(), port, meter.isSimulated())));
        device.markConnected(name, request.protocol());
        connection.connect(meter, new ConnectionSession(userId, device.getId(), device.getName()));
        return status(userId);
    }

    public DeviceStatusResponse disconnect(Long userId) {
        requireOwner(userId);
        connection.disconnect();
        return status(userId);
    }

    public DeviceStatusResponse status(Long userId) {
        DeviceSnapshot s = connection.snapshot();
        boolean mine = s.ownerId() != null && s.ownerId().equals(userId);
        if (!mine && s.state() != DeviceState.DISCONNECTED) {
            return new DeviceStatusResponse(s.state().name(), s.state().label() + " (otra cuenta)", false, null, null,
                null, null, null, false, false, null, null);
        }
        return new DeviceStatusResponse(s.state().name(), s.state().label(), mine, s.deviceId(), s.deviceName(), s.port(),
            s.protocol() == null ? null : s.protocol().name(), s.protocol() == null ? null : s.protocol().label(),
            s.simulated(), s.supportsCalibration(), s.connectedAt(), s.lastError());
    }

    @Transactional
    public ReadingPreviewResponse measure(Long userId) {
        DeviceSnapshot snapshot = requireOwner(userId);
        PhReading reading = invoker.run(userId, new MeasureCommand(measureSamples));
        MeasurementSource source = snapshot.simulated() ? MeasurementSource.SIMULATED : MeasurementSource.REAL;
        NewReading newReading = new NewReading(reading.ph(), reading.temperatureC(), reading.at(), source,
            snapshot.deviceId(), null);
        boolean autoSave = settingsRepository.findById(userId).map(UserSettings::isAutoSave).orElse(false);
        MeasurementService.Preview preview = measurementService.preview(userId, newReading);
        if (autoSave && !preview.report().isRejected()) {
            Measurement saved = measurementService.record(userId, newReading);
            return toPreview(null, newReading, preview, snapshot.deviceName(), measurementService.toResponse(userId, saved));
        }
        cleanupPending();
        String pendingId = UUID.randomUUID().toString();
        pending.put(pendingId, new PendingReading(userId, newReading, Instant.now()));
        return toPreview(pendingId, newReading, preview, snapshot.deviceName(), null);
    }

    @Transactional
    public MeasurementResponse savePending(Long userId, String pendingId, String note) {
        PendingReading reading = pending.get(pendingId);
        if (reading == null || !reading.userId().equals(userId) || reading.isExpired()) {
            throw BusinessException.notFound("La lectura ya no está disponible. Realiza una nueva medición.");
        }
        pending.remove(pendingId);
        NewReading withNote = new NewReading(reading.reading().ph(), reading.reading().temperatureC(),
            reading.reading().measuredAt(), reading.reading().source(), reading.reading().deviceId(), note);
        return measurementService.toResponse(userId, measurementService.record(userId, withNote));
    }

    public String calibrate(Long userId, CalibrateRequest request) {
        requireOwner(userId);
        double buffer = request.bufferValue() == null ? request.point().defaultBuffer() : request.bufferValue();
        return invoker.run(userId, new CalibrateCommand(request.point(), buffer));
    }

    public DeviceStatusResponse startLive(Long userId) {
        requireOwner(userId);
        invoker.run(userId, new StartLiveCommand());
        return status(userId);
    }

    public DeviceStatusResponse stopLive(Long userId) {
        requireOwner(userId);
        invoker.run(userId, new StopLiveCommand());
        return status(userId);
    }

    private DeviceSnapshot requireOwner(Long userId) {
        DeviceSnapshot snapshot = connection.snapshot();
        if (snapshot.state() == DeviceState.DISCONNECTED) {
            throw BusinessException.conflict("No hay un medidor conectado. Conecta uno en la sección Conexión.");
        }
        if (snapshot.ownerId() != null && !snapshot.ownerId().equals(userId)) {
            throw BusinessException.conflict("El medidor está en uso por otra cuenta.");
        }
        return snapshot;
    }

    private ReadingPreviewResponse toPreview(String pendingId, NewReading reading, MeasurementService.Preview preview,
                                             String deviceName, MeasurementResponse saved) {
        List<String> messages = new ArrayList<>(preview.report().errors());
        messages.addAll(preview.report().warnings());
        return new ReadingPreviewResponse(pendingId, reading.ph(), reading.temperatureC(), reading.measuredAt(),
            reading.source().name(), reading.source().label(), preview.classification().nature().name(),
            preview.classification().nature().label(), preview.classification().status().name(),
            preview.classification().status().label(), preview.classification().explanation(),
            preview.report().status().name(), preview.report().status().label(), messages, deviceName,
            preview.profile().getName(), saved != null, saved);
    }

    private void cleanupPending() {
        pending.values().removeIf(PendingReading::isExpired);
    }

    private static String defaultName(MeterProtocol protocol, String port) {
        return protocol == MeterProtocol.SIMULATOR ? "Simulador AquaSense" : "Medidor pH " + port;
    }

    private record PendingReading(Long userId, NewReading reading, Instant createdAt) {
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(PENDING_TTL));
        }
    }
}

package com.poolguard.report;

import com.poolguard.model.AlertRecord;
import com.poolguard.model.MaintenanceRecord;
import com.poolguard.model.SensorReading;
import com.poolguard.model.Thresholds;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates automatic sanitary compliance reports, ready to export.
 */
public class ReportGenerator {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    /** Builds the plain-text report in Spanish. */
    public String generate(String poolName, List<SensorReading> readings, List<AlertRecord> alerts,
                           List<MaintenanceRecord> maintenances, Thresholds thresholds) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("============================================================\n");
        sb.append(" REPORTE DE CUMPLIMIENTO SANITARIO - PoolGuard\n");
        sb.append("============================================================\n");
        sb.append("Piscina:        ").append(poolName).append('\n');
        sb.append("Fecha emisión:  ").append(FORMATTER.format(Instant.now())).append('\n');
        sb.append("------------------------------------------------------------\n\n");

        sb.append("1. PARÁMETROS DE REFERENCIA\n");
        sb.append("   pH óptimo:        ").append(thresholds.getPhOptimalMin()).append(" - ").append(thresholds.getPhOptimalMax()).append('\n');
        sb.append("   pH precaución:    ").append(thresholds.getPhCautionMin()).append(" - ").append(thresholds.getPhCautionMax()).append('\n');
        sb.append("   pH crítico:       < ").append(thresholds.getPhCriticalMin()).append(" o > ").append(thresholds.getPhCriticalMax()).append('\n');
        sb.append("   Cloro óptimo:     ").append(thresholds.getChlorineOptimalMin()).append(" - ").append(thresholds.getChlorineOptimalMax()).append(" ppm\n");
        sb.append("   Cloro precaución: ").append(thresholds.getChlorineCautionMin()).append(" - ").append(thresholds.getChlorineCautionMax()).append(" ppm\n\n");

        sb.append("2. RESUMEN DEL PERÍODO\n");
        sb.append("   Lecturas analizadas: ").append(readings.size()).append('\n');
        if (!readings.isEmpty()) {
            double minPh = readings.stream().mapToDouble(SensorReading::getPh).min().orElse(0);
            double maxPh = readings.stream().mapToDouble(SensorReading::getPh).max().orElse(0);
            double avgPh = readings.stream().mapToDouble(SensorReading::getPh).average().orElse(0);
            double minCl = readings.stream().mapToDouble(SensorReading::getChlorinePpm).min().orElse(0);
            double maxCl = readings.stream().mapToDouble(SensorReading::getChlorinePpm).max().orElse(0);
            double avgTemp = readings.stream().mapToDouble(SensorReading::getTemperatureC).average().orElse(0);
            sb.append("   pH mínimo / medio / máximo: ")
              .append(minPh).append(" / ").append(String.format("%.2f", avgPh)).append(" / ").append(maxPh).append('\n');
            sb.append("   Cloro mínimo / máximo:      ")
              .append(minCl).append(" / ").append(maxCl).append(" ppm\n");
            sb.append("   Temperatura media:           ").append(String.format("%.2f", avgTemp)).append(" °C\n");
        }
        sb.append('\n');

        sb.append("3. ALERTAS GENERADAS (").append(alerts.size()).append(")\n");
        for (AlertRecord a : alerts) {
            sb.append("   [").append(FORMATTER.format(Instant.ofEpochMilli(a.getTimestamp())))
              .append("] (").append(a.getType()).append('/').append(a.getSeverity()).append(") ")
              .append(a.getMessageEs()).append('\n');
        }
        sb.append('\n');

        sb.append("4. MANTENIMIENTOS REGISTRADOS (").append(maintenances.size()).append(")\n");
        for (MaintenanceRecord m : maintenances) {
            sb.append("   [").append(FORMATTER.format(Instant.ofEpochMilli(m.getTimestamp())))
              .append("] ").append(m.getPerformedBy()).append(": ").append(m.getDescription());
            if (m.getChemicalUsed() != null && !m.getChemicalUsed().isEmpty()) {
                sb.append(" (").append(m.getChemicalUsed()).append(' ').append(m.getQuantity()).append(' ').append(m.getUnit()).append(')');
            }
            sb.append('\n');
        }
        sb.append('\n');

        sb.append("============================================================\n");
        sb.append(" Documento generado automáticamente por PoolGuard\n");
        sb.append("============================================================\n");
        return sb.toString();
    }
}

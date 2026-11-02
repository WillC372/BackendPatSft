package com.poolguard.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded file-based DataStore. Each table is persisted as a JSON file
 * inside the configured data directory (database/data by default).
 *
 * Writes are buffered in memory and flushed to disk on UnitOfWork.commit(),
 * which gives the application an offline-capable, auto-synchronizing store.
 */
public class JsonDataStore implements DataStore {

    private final Path dataDir;
    private final Map<String, List<Map<String, Object>>> cache = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> idSequences = new ConcurrentHashMap<>();

    public JsonDataStore(String dataDir) {
        this.dataDir = Paths.get(dataDir);
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data directory: " + this.dataDir, e);
        }
    }

    @Override
    public synchronized List<Map<String, Object>> loadTable(String table) {
        if (cache.containsKey(table)) {
            return cache.get(table);
        }
        Path file = dataDir.resolve(table + ".json");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (Files.exists(file)) {
            try {
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (!content.trim().isEmpty()) {
                    Object parsed = Json.parse(content);
                    if (parsed instanceof List) {
                        for (Object row : (List<?>) parsed) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> r = (Map<String, Object>) row;
                            rows.add(r);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: could not load " + file + ": " + e.getMessage());
            }
        }
        cache.put(table, rows);
        return rows;
    }

    @Override
    public synchronized void saveTable(String table, List<Map<String, Object>> rows) {
        cache.put(table, new ArrayList<>(rows));
        markDirty(table);
    }

    @Override
    public synchronized void markDirty(String table) {
        dirty.add(table);
    }

    @Override
    public synchronized void flush() {
        for (String table : dirty) {
            List<Map<String, Object>> rows = cache.get(table);
            if (rows == null) {
                continue;
            }
            try {
                Path file = dataDir.resolve(table + ".json");
                Files.write(file, Json.write(rows).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException("Failed to persist table " + table, e);
            }
        }
        dirty.clear();
    }

    @Override
    public synchronized long nextId(String table) {
        long max = idSequences.getOrDefault(table, 0L);
        for (Map<String, Object> row : loadTable(table)) {
            Object id = row.get("id");
            if (id instanceof Number) {
                max = Math.max(max, ((Number) id).longValue());
            }
        }
        max++;
        idSequences.put(table, max);
        return max;
    }
}

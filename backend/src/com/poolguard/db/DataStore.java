package com.poolguard.db;

import java.util.List;
import java.util.Map;

/**
 * Low-level persistence abstraction. A "table" is a list of row maps.
 * Implementations: JsonDataStore (embedded) or a future JDBCDataStore (MySQL).
 */
public interface DataStore {

    /** Loads all rows of a table (cached in memory). */
    List<Map<String, Object>> loadTable(String table);

    /** Replaces the whole content of a table and marks it dirty. */
    void saveTable(String table, List<Map<String, Object>> rows);

    /** Marks a table as modified (part of the Unit of Work commit protocol). */
    void markDirty(String table);

    /** Commits every pending change to the persistent medium. */
    void flush();

    /** Returns the next sequential id for a table. */
    long nextId(String table);
}

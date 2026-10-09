package com.poolguard.patterns.factory;

import com.poolguard.db.DataStore;
import com.poolguard.db.DatabaseConfig;
import com.poolguard.db.JsonDataStore;

/**
 * ABSTRACT FACTORY PATTERN
 *
 * Creates the database connection / persistence layer. The client code only
 * sees the DataStore interface, so the engine can be switched
 * (embedded JSON <-> MySQL) without touching the business logic.
 */
public class DatabaseConnectionFactory {

    public enum DbEngine {
        JSON_EMBEDDED, MYSQL
    }

    /** Creates a DataStore for the requested engine. */
    public DataStore createDataStore(DbEngine engine) {
        switch (engine) {
            case JSON_EMBEDDED:
                return new JsonDataStore(DatabaseConfig.getEmbeddedDataDir());
            case MYSQL:
                // Configure DatabaseConfig (JDBC url/user/password) and add the
                // MySQL JDBC driver to the classpath to enable this engine.
                throw new UnsupportedOperationException(
                        "MySQL adapter: configure DatabaseConfig and add the JDBC driver");
            default:
                throw new IllegalArgumentException("Unknown engine: " + engine);
        }
    }
}

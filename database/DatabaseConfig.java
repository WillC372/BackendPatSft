package com.poolguard.db;

/**
 * Database connection configuration.
 *
 * The application supports two engines (selected through the
 * DatabaseConnectionFactory):
 *
 *  - JSON_EMBEDDED: file-based store under database/data (default, zero-setup)
 *  - MYSQL:         relational database using the schema in database/schema.sql
 *
 * JDBC credentials can be overridden with environment variables:
 *   POOLGUARD_JDBC_URL, POOLGUARD_DB_USER, POOLGUARD_DB_PASSWORD
 */
public final class DatabaseConfig {

    private DatabaseConfig() {
    }

    /** JDBC URL used when the MYSQL engine is selected. */
    public static String getJdbcUrl() {
        return System.getenv().getOrDefault(
                "POOLGUARD_JDBC_URL", "jdbc:mysql://localhost:3306/poolguard");
    }

    /** JDBC user used when the MYSQL engine is selected. */
    public static String getJdbcUser() {
        return System.getenv().getOrDefault("POOLGUARD_DB_USER", "root");
    }

    /** JDBC password used when the MYSQL engine is selected. */
    public static String getJdbcPassword() {
        return System.getenv().getOrDefault("POOLGUARD_DB_PASSWORD", "");
    }

    /** Directory of the embedded JSON data files. */
    public static String getEmbeddedDataDir() {
        String env = System.getenv("POOLGUARD_DATA_DIR");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        if (new java.io.File("database/data").exists() || new java.io.File("database").exists()) {
            return "database/data";
        }
        if (new java.io.File("../database/data").exists() || new java.io.File("../database").exists()) {
            return "../database/data";
        }
        return "database/data";
    }
}

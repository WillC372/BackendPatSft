package com.poolguard.patterns.repository;

import com.poolguard.db.DataStore;

/**
 * UNIT OF WORK PATTERN
 *
 * Coordinates the persistence work of every repository. Changes accumulate
 * in memory (offline-capable) and are committed atomically with commit(),
 * which flushes all pending writes to the database at once. When the
 * connection is recovered, the pending changes synchronize automatically.
 */
public class UnitOfWork {

    private final DataStore store;

    public UnitOfWork(DataStore store) {
        this.store = store;
    }

    /** Atomically commits every pending change. */
    public void commit() {
        store.flush();
    }

    public DataStore getStore() {
        return store;
    }
}

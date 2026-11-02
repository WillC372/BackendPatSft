package com.poolguard.patterns.repository;

import java.util.List;

/**
 * REPOSITORY PATTERN
 *
 * Generic contract for persistence operations. Hides the storage medium
 * (embedded JSON or MySQL) from the rest of the application.
 */
public interface Repository<T, ID> {

    T findById(ID id);

    List<T> findAll();

    void save(T entity);

    void delete(ID id);
}

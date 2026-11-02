package com.poolguard.patterns.repository;

import com.poolguard.db.DataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Base implementation of the Repository pattern over a DataStore.
 * Concrete repositories only declare their table and row mapper.
 */
public abstract class BaseRepository<T, ID> implements Repository<T, ID> {

    protected final DataStore store;
    protected final String table;
    protected final Function<Map<String, Object>, T> mapper;

    protected BaseRepository(DataStore store, String table, Function<Map<String, Object>, T> mapper) {
        this.store = store;
        this.table = table;
        this.mapper = mapper;
    }

    protected List<Map<String, Object>> rows() {
        return store.loadTable(table);
    }

    @Override
    public T findById(ID id) {
        for (Map<String, Object> row : rows()) {
            if (Objects.equals(row.get("id"), id)) {
                return mapper.apply(row);
            }
        }
        return null;
    }

    @Override
    public List<T> findAll() {
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : rows()) {
            result.add(mapper.apply(row));
        }
        return result;
    }

    @Override
    public void save(T entity) {
        List<Map<String, Object>> rows = rows();
        Map<String, Object> map = toMap(entity);
        Object idValue = map.get("id");
        if (idValue == null || ((Number) idValue).longValue() == 0) {
            map.put("id", store.nextId(table));
        }
        long id = ((Number) map.get("id")).longValue();
        long finalId = id;
        rows.removeIf(r -> Objects.equals(r.get("id"), finalId));
        rows.add(map);
        store.saveTable(table, rows);
    }

    @Override
    public void delete(ID id) {
        List<Map<String, Object>> rows = rows();
        rows.removeIf(r -> Objects.equals(r.get("id"), id));
        store.saveTable(table, rows);
    }

    /** Converts the entity into a row map. */
    protected abstract Map<String, Object> toMap(T entity);
}

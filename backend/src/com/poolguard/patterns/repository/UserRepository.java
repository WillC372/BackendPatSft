package com.poolguard.patterns.repository;

import com.poolguard.auth.User;
import com.poolguard.db.DataStore;

import java.util.Map;

/**
 * Repository for users (credentials + RBAC roles).
 */
public class UserRepository extends BaseRepository<User, Long> {

    public UserRepository(DataStore store) {
        super(store, "users", User::fromMap);
    }

    /** Finds a user by its unique username (case-insensitive). */
    public User findByUsername(String username) {
        for (User user : findAll()) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                return user;
            }
        }
        return null;
    }

    @Override
    protected Map<String, Object> toMap(User entity) {
        return entity.toMap();
    }
}

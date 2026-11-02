package com.poolguard.auth;

import com.poolguard.patterns.repository.UserRepository;

/**
 * Authentication service: verifies credentials against the hashed passwords
 * stored in the database and manages user registration.
 */
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns the authenticated user, or null if credentials are invalid. */
    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    /** Registers a new user (password must already be hashed). */
    public void registerUser(User user) {
        userRepository.save(user);
    }
}

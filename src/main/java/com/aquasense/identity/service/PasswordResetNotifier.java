package com.aquasense.identity.service;

public interface PasswordResetNotifier {

    void send(String email, String displayName, String resetCode, long validMinutes);
}

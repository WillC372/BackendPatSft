package com.aquasense.user.dto;

/**
 * Who the token belongs to and which session it is: answer of {@code GET /api/v1/auth/me}. The page uses it on load
 * to check that the saved token still works and to show the account and when the session ends.
 */
public record AuthMeResponse(UserResponse user, SessionDtos.SessionSummary session) {
}

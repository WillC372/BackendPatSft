package com.aquasense.user;

import com.aquasense.security.CurrentUser;
import com.aquasense.user.dto.MessageResponse;
import com.aquasense.user.dto.SessionDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** History of the user's own sessions: one entry per login, with what was done in it. */
@RestController
@RequestMapping("/api/v1/users/me/sessions")
public class SessionController {

    private final SessionHistoryService sessions;
    private final AuthService authService;

    public SessionController(SessionHistoryService sessions, AuthService authService) {
        this.sessions = sessions;
        this.authService = authService;
    }

    @GetMapping
    public SessionDtos.SessionPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return sessions.list(CurrentUser.id(), CurrentUser.sessionId(), page, size);
    }

    @GetMapping("/{id}")
    public SessionDtos.SessionDetail detail(@PathVariable Long id) {
        return sessions.detail(CurrentUser.id(), id, CurrentUser.sessionId());
    }

    /** Closes the sessions open in other browsers or devices; the current one stays open. */
    @PostMapping("/close-others")
    public MessageResponse closeOthers() {
        int closed = authService.closeOtherSessions(CurrentUser.id(), CurrentUser.sessionId());
        return new MessageResponse(closed == 0 ? "No había otras sesiones abiertas."
            : closed == 1 ? "Se cerró 1 sesión abierta en otro equipo." : "Se cerraron " + closed + " sesiones abiertas en otros equipos.");
    }
}

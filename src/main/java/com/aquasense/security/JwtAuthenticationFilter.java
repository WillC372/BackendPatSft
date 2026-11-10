package com.aquasense.security;

import com.aquasense.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final com.aquasense.user.SessionHistoryService sessions;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   com.aquasense.user.SessionHistoryService sessions) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            // Valid token: correct signature, enabled account, current token version and a session that is still open.
            jwtService.parse(header.substring(BEARER_PREFIX.length()))
                .filter(claims -> claims.sessionId() != null && sessions.touch(claims.sessionId(), claims.userId()))
                .flatMap(claims -> userRepository.findById(claims.userId())
                    .filter(user -> user.isEnabled() && claims.version() != null && user.getTokenVersion() == claims.version())
                    .map(user -> new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name(), claims.sessionId())))
                .ifPresent(principal -> {
                    var authority = new SimpleGrantedAuthority("ROLE_" + principal.role());
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        }
        chain.doFilter(request, response);
    }
}

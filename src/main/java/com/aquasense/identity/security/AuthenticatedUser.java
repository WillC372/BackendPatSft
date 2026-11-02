package com.aquasense.identity.security;

import com.aquasense.identity.domain.RoleName;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(UUID id, List<RoleName> roles) {
}

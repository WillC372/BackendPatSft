package com.aquasense.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.identity.domain.RoleName;
import com.aquasense.identity.domain.User;
import com.aquasense.identity.security.JwtService;
import com.aquasense.identity.security.SecurityProperties;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-0123456789-abcdef";

    private final JwtService service = new JwtService(new SecurityProperties(SECRET, 15, 7, 30, 30));

    @Test
    void roundTripsSubjectAndRoles() {
        User user = new User("a@b.co", null, "Ana", "hash");
        String token = service.createAccessToken(user);
        var parsed = service.parse(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().id()).isEqualTo(user.getId());
        assertThat(parsed.get().roles()).containsExactly(RoleName.USER);
    }

    @Test
    void rejectsTamperedAndForeignTokens() {
        User user = new User("a@b.co", null, "Ana", "hash");
        String token = service.createAccessToken(user);
        assertThat(service.parse(token + "x")).isEmpty();
        JwtService other = new JwtService(new SecurityProperties("another-secret-0123456789-0123456789-xyz", 15, 7, 30, 30));
        assertThat(other.parse(token)).isEmpty();
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsWeakSecret() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new SecurityProperties("short", 15, 7, 30, 30));
    }
}

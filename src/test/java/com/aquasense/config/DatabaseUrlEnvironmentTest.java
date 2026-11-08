package com.aquasense.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The hosted-database connection string becomes the JDBC URL, user and password Spring expects. */
class DatabaseUrlEnvironmentTest {

    @Test
    void convertsNeonConnectionString() {
        Map<String, Object> props = DatabaseUrlEnvironment.toSpring(
            "postgresql://aquasense_owner:p%40ss%3Aword@ep-cool-river-123.us-east-2.aws.neon.tech/neondb?sslmode=require");
        assertThat(props).containsEntry("spring.datasource.url",
                "jdbc:postgresql://ep-cool-river-123.us-east-2.aws.neon.tech/neondb?sslmode=require")
            .containsEntry("spring.datasource.username", "aquasense_owner")
            .containsEntry("spring.datasource.password", "p@ss:word");
    }

    @Test
    void keepsThePortAndAcceptsThePostgresScheme() {
        assertThat(DatabaseUrlEnvironment.toSpring("postgres://u:p@db.example.com:6543/app").get("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://db.example.com:6543/app");
        assertThatThrownBy(() -> DatabaseUrlEnvironment.toSpring("mysql://u:p@h/db")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitDbUrlWins() {
        MockEnvironment env = new MockEnvironment().withProperty("DATABASE_URL", "postgresql://u:p@h/db")
            .withProperty("DB_URL", "jdbc:postgresql://other/db");
        new DatabaseUrlEnvironment().postProcessEnvironment(env, null);
        assertThat(env.getPropertySources().contains(DatabaseUrlEnvironment.SOURCE)).isFalse();
        MockEnvironment only = new MockEnvironment().withProperty("DATABASE_URL", "postgresql://u:p@h/db");
        new DatabaseUrlEnvironment().postProcessEnvironment(only, null);
        assertThat(only.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://h/db");
    }
}

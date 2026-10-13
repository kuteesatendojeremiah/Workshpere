package com.worksphere.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.worksphere.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class UsersTableMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usersTableExistsWithExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'users'",
                String.class);

        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "id",
                        "email",
                        "password_hash",
                        "display_name",
                        "email_verified",
                        "created_at",
                        "updated_at");
    }

    @Test
    void emailHasUniqueConstraint() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name) VALUES (?, ?, ?)",
                "unique-constraint-check@example.com", "hash", "First User");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO users (email, password_hash, display_name) VALUES (?, ?, ?)",
                        "unique-constraint-check@example.com", "hash", "Second User"));
    }
}

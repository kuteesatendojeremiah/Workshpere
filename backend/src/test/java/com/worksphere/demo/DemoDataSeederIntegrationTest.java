package com.worksphere.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.demo=true")
class DemoDataSeederIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void theDemoAccountCanSignInAndSeesTheSeededOrganizationProjectsAndTasks() throws Exception {
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + DemoDataSeeder.DEMO_EMAIL + "\", \"password\": \""
                                + DemoDataSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", equalTo("Acme Demo")));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM projects p JOIN organizations o ON o.id = p.organization_id WHERE o.name = 'Acme Demo'",
                        Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForList(
                        "SELECT DISTINCT t.status FROM tasks t JOIN projects p ON p.id = t.project_id "
                                + "JOIN organizations o ON o.id = p.organization_id WHERE o.name = 'Acme Demo' ORDER BY t.status",
                        String.class))
                .containsExactlyInAnyOrder("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE");
    }

    @Test
    void runningTheSeederAgainChangesNothing() {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM organizations WHERE name = 'Acme Demo'", Integer.class);

        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM organizations WHERE name = 'Acme Demo'", Integer.class))
                .isEqualTo(before);
    }
}

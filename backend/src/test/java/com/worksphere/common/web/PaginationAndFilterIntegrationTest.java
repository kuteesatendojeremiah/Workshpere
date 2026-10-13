package com.worksphere.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Spec 24.1: "Invalid pagination/filter values are rejected safely". "Safely" means a clean 400
 * in the standard error format - never a 500, never a stack trace, never an unbounded result set.
 */
@AutoConfigureMockMvc
class PaginationAndFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String auth;
    private UUID orgId;
    private UUID projectId;

    @BeforeEach
    void createOrganizationAndProject() throws Exception {
        User owner = userRepository.save(new User(
                "paging-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode("password123"), "Pager"));
        auth = "Bearer " + jwtService.generateAccessToken(owner.getId());
        orgId = create("/api/v1/organizations", "{\"name\": \"Paging Org\"}");
        projectId = create("/api/v1/organizations/" + orgId + "/projects", "{\"name\": \"Paging Project\"}");
        create("/api/v1/projects/" + projectId + "/tasks", "{\"title\": \"One\"}");
    }

    @Test
    void negativePageZeroSizeAndNonNumericValuesAreRejectedWithA400() throws Exception {
        for (String query : new String[] {"page=-1", "size=0", "size=-5", "page=abc", "size=lots", "size=99999999999"}) {
            expectValidationError(get("/api/v1/projects/{p}/tasks?" + query, projectId));
        }
    }

    @Test
    void oversizedPageSizeIsCappedNotHonoured() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/tasks?size=100000", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(200)));
    }

    @Test
    void unknownSortPropertyIsRejectedWithA400() throws Exception {
        expectValidationError(get("/api/v1/projects/{p}/tasks?sort=doesNotExist,asc", projectId));
        expectValidationError(get("/api/v1/organizations/{o}/projects?sort=passwordHash,desc", orgId));
    }

    @Test
    void invalidEnumAndUuidFiltersAreRejectedWithA400() throws Exception {
        expectValidationError(get("/api/v1/projects/{p}/tasks?status=NOT_A_STATUS", projectId));
        expectValidationError(get("/api/v1/projects/{p}/tasks?priority=SUPER_URGENT", projectId));
        expectValidationError(get("/api/v1/projects/{p}/tasks?assigneeId=not-a-uuid", projectId));
    }

    @Test
    void malformedPathIdIsRejectedWithA400() throws Exception {
        expectValidationError(get("/api/v1/tasks/not-a-uuid"));
    }

    private void expectValidationError(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        ResultActions result = mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, auth));
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("\tat "))));
    }

    private UUID create(String path, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}

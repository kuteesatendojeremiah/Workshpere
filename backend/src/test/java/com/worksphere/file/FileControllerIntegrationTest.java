package com.worksphere.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real PostgreSQL + real MinIO: upload, download, access control, validation, audit, clean-up. */
@AutoConfigureMockMvc
class FileControllerIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Setup(User owner, UUID orgId, UUID projectId, UUID taskId) {
    }

    private Setup setup(String tag) throws Exception {
        User owner = createUser("files-" + tag + "@example.com");
        UUID orgId = idOf(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Files " + tag + "\"}"));
        UUID projectId = idOf(post("/api/v1/organizations/{o}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"P\"}"));
        UUID taskId = idOf(post("/api/v1/projects/{p}/tasks", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"T\"}"));
        return new Setup(owner, orgId, projectId, taskId);
    }

    private MockHttpServletRequestBuilder upload(UUID taskId, User user, String filename, String type, byte[] bytes) {
        return multipart("/api/v1/tasks/{t}/attachments", taskId)
                .file(new MockMultipartFile("file", filename, type, bytes))
                .header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    @Test
    void uploadThenListThenDownloadReturnsTheSameBytesWithSafeHeaders() throws Exception {
        Setup s = setup("roundtrip");

        MvcResult created = mockMvc.perform(upload(s.taskId, s.owner, "../logo.png", "image/png", PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename", equalTo("logo.png")))
                .andExpect(jsonPath("$.contentType", equalTo("image/png")))
                .andExpect(jsonPath("$.sizeBytes", equalTo(PNG.length)))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn();
        String fileId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/tasks/{t}/attachments", s.taskId).header(HttpHeaders.AUTHORIZATION, bearer(s.owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", equalTo(1)));

        MvcResult download = mockMvc.perform(get("/api/v1/attachments/{f}/download", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(s.owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
    }

    @Test
    void badFilesAreRefusedAndNothingIsRecorded() throws Exception {
        Setup s = setup("bad");
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

        mockMvc.perform(upload(s.taskId, s.owner, "setup.exe", "application/octet-stream", exe))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", equalTo("INVALID_FILE")));
        mockMvc.perform(upload(s.taskId, s.owner, "page.html", "text/plain", "<script>x</script>".getBytes()))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(upload(s.taskId, s.owner, "photo.png", "image/png", "%PDF-1.7 not a png".getBytes()))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(upload(s.taskId, s.owner, "empty.txt", "text/plain", new byte[0]))
                .andExpect(status().isBadRequest());
        mockMvc.perform(upload(s.taskId, s.owner, "huge.txt", "text/plain", new byte[11 * 1024 * 1024]))
                .andExpect(status().isPayloadTooLarge());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM file_attachments WHERE task_id = ?", Integer.class, s.taskId))
                .isZero();
    }

    @Test
    void anotherOrganizationCanNeitherSeeDownloadNorDeleteTheFile() throws Exception {
        Setup victim = setup("victim");
        Setup attacker = setup("attacker");
        MvcResult created = mockMvc.perform(upload(victim.taskId, victim.owner, "secret.txt", "text/plain", "top secret".getBytes()))
                .andExpect(status().isCreated())
                .andReturn();
        String fileId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String attackerAuth = bearer(attacker.owner);

        mockMvc.perform(get("/api/v1/attachments/{f}/download", fileId).header(HttpHeaders.AUTHORIZATION, attackerAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/attachments/{f}", fileId).header(HttpHeaders.AUTHORIZATION, attackerAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tasks/{t}/attachments", victim.taskId).header(HttpHeaders.AUTHORIZATION, attackerAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(upload(victim.taskId, attacker.owner, "planted.txt", "text/plain", "x".getBytes()))
                .andExpect(status().isNotFound());

        // Not signed in at all.
        mockMvc.perform(get("/api/v1/attachments/{f}/download", fileId)).andExpect(status().isUnauthorized());

        // The victim still has it.
        mockMvc.perform(get("/api/v1/attachments/{f}/download", fileId).header(HttpHeaders.AUTHORIZATION, bearer(victim.owner)))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAFileRemovesItAndIsAudited() throws Exception {
        Setup s = setup("delete");
        MvcResult created = mockMvc.perform(upload(s.taskId, s.owner, "notes.txt", "text/plain", "hello".getBytes()))
                .andExpect(status().isCreated())
                .andReturn();
        String fileId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/attachments/{f}", fileId).header(HttpHeaders.AUTHORIZATION, bearer(s.owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/attachments/{f}/download", fileId).header(HttpHeaders.AUTHORIZATION, bearer(s.owner)))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForList(
                        "SELECT action FROM audit_logs WHERE resource_id = ?::uuid ORDER BY created_at", String.class, fileId))
                .containsExactly("FILE_UPLOADED", "FILE_DELETED");
    }

    @Test
    void deletingATaskDeletesItsAttachments() throws Exception {
        Setup s = setup("taskdelete");
        mockMvc.perform(upload(s.taskId, s.owner, "notes.txt", "text/plain", "hello".getBytes()))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM file_attachments WHERE task_id = ?", Integer.class, s.taskId))
                .isEqualTo(1);

        mockMvc.perform(delete("/api/v1/tasks/{t}", s.taskId).header(HttpHeaders.AUTHORIZATION, bearer(s.owner)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM file_attachments WHERE task_id = ?", Integer.class, s.taskId))
                .isZero();
    }

    private UUID idOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private User createUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId());
    }
}

package com.worksphere.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.TestRoles;
import com.worksphere.identity.User;
import com.worksphere.organization.Organization;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import com.worksphere.project.Project;
import com.worksphere.task.Task;
import com.worksphere.task.TaskDeletedEvent;
import com.worksphere.task.TaskPriority;
import com.worksphere.task.TaskService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    /** A real in-memory storage, so the tests check what was stored rather than how it was called. */
    private static final class FakeStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void put(String key, byte[] content, String contentType) {
            objects.put(key, content);
        }

        @Override
        public InputStream get(String key) {
            return new ByteArrayInputStream(objects.get(key));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Mock
    private FileAttachmentRepository fileRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private AuditService auditService;

    private final FakeStorage storage = new FakeStorage();
    private final PermissionChecker permissionChecker = new PermissionChecker();
    private FileService fileService;
    private Task task;
    private final UUID taskId = UUID.randomUUID();
    private final UUID uploaderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fileService = new FileService(
                fileRepository, storage, taskService, organizationService, permissionChecker, auditService);
        task = new Task(new Project(new Organization("Acme"), "Website", null), "Task", null, TaskPriority.MEDIUM, null);
    }

    private void taskIsVisible() {
        when(taskService.getTask(taskId, uploaderId)).thenReturn(task);
    }

    @Test
    void uploadStoresTheBytesUnderAGeneratedKeyAndRecordsTheDetectedType() {
        taskIsVisible();
        when(fileRepository.saveAndFlush(any(FileAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAttachment saved = fileService.upload(taskId, uploaderId, "../../logo.PNG", "image/png", PNG);

        assertThat(saved.getOriginalFilename()).as("path stripped").isEqualTo("logo.PNG");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getSizeBytes()).isEqualTo(PNG.length);
        assertThat(saved.getSha256()).hasSize(64);
        assertThat(saved.getStorageKey()).doesNotContain("logo").doesNotContain("..");
        assertThat(storage.objects).containsKey(saved.getStorageKey());
        verify(auditService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadRejectsABadFileBeforeAnythingIsStored() {
        taskIsVisible();
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

        assertThatThrownBy(() -> fileService.upload(taskId, uploaderId, "setup.exe", "application/octet-stream", exe))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> fileService.upload(taskId, uploaderId, "page.html", "text/plain", "<b>x</b>".getBytes()))
                .as("text content with an html extension")
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> fileService.upload(taskId, uploaderId, "photo.png", "image/png", "%PDF-1.7".getBytes()))
                .as("a PDF pretending to be a PNG")
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> fileService.upload(taskId, uploaderId, "photo.png", "application/pdf", PNG))
                .as("declared content type disagrees")
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> fileService.upload(taskId, uploaderId, "empty.txt", "text/plain", new byte[0]))
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> fileService.upload(
                        taskId, uploaderId, "big.txt", "text/plain", new byte[(int) FileService.MAX_BYTES + 1]))
                .isInstanceOf(InvalidFileException.class);

        assertThat(storage.objects).isEmpty();
        verify(fileRepository, never()).saveAndFlush(any());
    }

    @Test
    void anAcceptedFileSavedWithAnUndeclaredContentTypeIsStillChecked() {
        taskIsVisible();
        when(fileRepository.saveAndFlush(any(FileAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAttachment saved = fileService.upload(taskId, uploaderId, "notes.txt", null, "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(saved.getContentType()).isEqualTo("text/plain");
    }

    @Test
    void theStoredObjectIsRemovedIfSavingTheMetadataFails() {
        taskIsVisible();
        when(fileRepository.saveAndFlush(any(FileAttachment.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> fileService.upload(taskId, uploaderId, "logo.png", "image/png", PNG))
                .isInstanceOf(IllegalStateException.class);

        assertThat(storage.objects).as("no orphaned object").isEmpty();
    }

    private FileAttachment storedFile(UUID uploader) {
        storage.objects.put("org/project/key", PNG);
        FileAttachment file = new FileAttachment(
                UUID.randomUUID(), UUID.randomUUID(), taskId, uploader, "logo.png", "image/png", PNG.length,
                "a".repeat(64), "org/project/key");
        UUID fileId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(file, "id", fileId);
        lenient().when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));
        return file;
    }

    @Test
    void theUploaderCanDeleteTheirOwnFile() {
        FileAttachment file = storedFile(uploaderId);
        when(taskService.getTask(taskId, uploaderId)).thenReturn(task);
        when(organizationService.requireMembership(any(), any()))
                .thenReturn(new OrganizationMember(new Organization("Acme"), new User("u@example.com", "h", "U"),
                        TestRoles.role("EMPLOYEE_MEMBER")));

        fileService.delete(file.getId(), uploaderId);

        verify(fileRepository).delete(file);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void someoneElseNeedsTheManageProjectsPermissionToDelete() {
        FileAttachment file = storedFile(UUID.randomUUID());
        UUID other = UUID.randomUUID();
        when(taskService.getTask(taskId, other)).thenReturn(task);
        when(organizationService.requireMembership(any(), any()))
                .thenReturn(new OrganizationMember(new Organization("Acme"), new User("u@example.com", "h", "U"),
                        TestRoles.role("EMPLOYEE_MEMBER")));

        assertThatThrownBy(() -> fileService.delete(file.getId(), other)).isInstanceOf(MissingPermissionException.class);

        verify(fileRepository, never()).delete(any());
        assertThat(storage.objects).isNotEmpty();
    }

    @Test
    void aManagerCanDeleteAnyonesFile() {
        FileAttachment file = storedFile(UUID.randomUUID());
        UUID manager = UUID.randomUUID();
        when(taskService.getTask(taskId, manager)).thenReturn(task);
        when(organizationService.requireMembership(any(), any()))
                .thenReturn(new OrganizationMember(new Organization("Acme"), new User("m@example.com", "h", "M"),
                        TestRoles.role("PROJECT_MANAGER", "MANAGE_PROJECTS")));

        fileService.delete(file.getId(), manager);

        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aFileOfATaskTheCallerCannotSeeLooksLikeItDoesNotExist() {
        FileAttachment file = storedFile(uploaderId);
        UUID outsider = UUID.randomUUID();
        when(taskService.getTask(taskId, outsider)).thenThrow(new IllegalStateException("not your task"));

        assertThatThrownBy(() -> fileService.open(file.getId(), outsider)).isInstanceOf(FileNotFoundApiException.class);
        assertThatThrownBy(() -> fileService.delete(file.getId(), outsider)).isInstanceOf(FileNotFoundApiException.class);
    }

    @Test
    void deletingATaskRemovesItsStoredFiles() {
        FileAttachment file = storedFile(uploaderId);
        when(fileRepository.findAllByTaskId(taskId)).thenReturn(List.of(file));

        fileService.onTaskDeleted(new TaskDeletedEvent(taskId));

        verify(fileRepository).deleteAll(List.of(file));
        assertThat(storage.objects).isEmpty();
    }
}

package com.worksphere.file;

import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.organization.OrganizationService;
import com.worksphere.task.Task;
import com.worksphere.task.TaskDeletedEvent;
import com.worksphere.task.TaskService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Task attachments (spec section 18). The rules, in the order they are applied on upload:
 *
 * <ol>
 *   <li>The caller must be able to see the task's project (authorization BEFORE reading the file).
 *   <li>Size: not empty, at most {@value #MAX_BYTES} bytes.
 *   <li>Type: detected from the file's own bytes against an allow-list, and the filename extension
 *       and declared Content-Type must agree with it.
 *   <li>The bytes go to object storage under a key WE generate; the user's filename is stored only
 *       as metadata and is never used as a path.
 *   <li>The upload and the delete are written to the audit log.
 * </ol>
 */
@Service
public class FileService {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String MANAGE_PROJECTS = "MANAGE_PROJECTS";
    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileAttachmentRepository fileRepository;
    private final ObjectStorage storage;
    private final TaskService taskService;
    private final OrganizationService organizationService;
    private final PermissionChecker permissionChecker;
    private final AuditService auditService;

    public FileService(
            FileAttachmentRepository fileRepository,
            ObjectStorage storage,
            TaskService taskService,
            OrganizationService organizationService,
            PermissionChecker permissionChecker,
            AuditService auditService) {
        this.fileRepository = fileRepository;
        this.storage = storage;
        this.taskService = taskService;
        this.organizationService = organizationService;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
    }

    /** A downloaded file: its metadata plus the open byte stream (the caller must close it). */
    public record Download(FileAttachment file, InputStream content) {
    }

    @Transactional
    public FileAttachment upload(
            UUID taskId, UUID callerUserId, String originalFilename, String declaredContentType, byte[] content) {
        Task task = taskService.getTask(taskId, callerUserId);

        if (content.length == 0) {
            throw new InvalidFileException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidFileException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than 10 MB");
        }

        String filename = sanitizeFilename(originalFilename);
        FileTypeSniffer.DetectedType detected = FileTypeSniffer.detect(content)
                .orElseThrow(() -> new InvalidFileException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "This type of file is not allowed (accepted: PNG, JPEG, GIF, PDF, plain text)"));
        if (!detected.extensions().contains(FileTypeSniffer.extensionOf(filename))) {
            throw new InvalidFileException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file name does not match what the file contains");
        }
        if (!declaredTypeAgrees(declaredContentType, detected.mimeType())) {
            throw new InvalidFileException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The declared content type does not match the file");
        }

        UUID organizationId = task.getProject().getOrganization().getId();
        UUID projectId = task.getProject().getId();
        String storageKey = organizationId + "/" + projectId + "/" + UUID.randomUUID();

        storage.put(storageKey, content, detected.mimeType());
        FileAttachment saved;
        try {
            saved = fileRepository.saveAndFlush(new FileAttachment(
                    organizationId, projectId, taskId, callerUserId, filename, detected.mimeType(),
                    content.length, sha256(content), storageKey));
        } catch (RuntimeException e) {
            deleteQuietly(storageKey); // do not leave an object nobody can ever find again
            throw e;
        }

        auditService.record(
                organizationId, callerUserId, AuditAction.FILE_UPLOADED, AuditResourceType.FILE, saved.getId(),
                Map.of("taskId", taskId.toString(), "contentType", detected.mimeType(), "sizeBytes", content.length));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<FileAttachment> list(UUID taskId, UUID callerUserId, Pageable pageable) {
        taskService.getTask(taskId, callerUserId);
        return fileRepository.findAllByTaskId(taskId, pageable);
    }

    /** Access is checked through the task BEFORE a single byte is read from storage. */
    @Transactional(readOnly = true)
    public Download open(UUID fileId, UUID callerUserId) {
        FileAttachment file = requireFileAccess(fileId, callerUserId);
        return new Download(file, storage.get(file.getStorageKey()));
    }

    @Transactional
    public void delete(UUID fileId, UUID callerUserId) {
        FileAttachment file = requireFileAccess(fileId, callerUserId);

        boolean isUploader = file.getUploadedBy().equals(callerUserId);
        boolean canManage = permissionChecker.hasPermission(
                organizationService.requireMembership(file.getOrganizationId(), callerUserId).getRole(),
                MANAGE_PROJECTS);
        if (!isUploader && !canManage) {
            throw new com.worksphere.common.error.MissingPermissionException(MANAGE_PROJECTS);
        }

        fileRepository.delete(file);
        auditService.record(
                file.getOrganizationId(), callerUserId, AuditAction.FILE_DELETED, AuditResourceType.FILE, fileId,
                Map.of("taskId", file.getTaskId().toString()));
        deleteObjectAfterCommit(file.getStorageKey());
    }

    /**
     * A deleted task takes its attachments with it. Runs inside the task-deleting transaction, before
     * the database cascades the rows away, so we still know which stored objects to remove.
     */
    @EventListener
    @Transactional
    public void onTaskDeleted(TaskDeletedEvent event) {
        List<FileAttachment> files = fileRepository.findAllByTaskId(event.taskId());
        fileRepository.deleteAll(files);
        files.forEach(file -> deleteObjectAfterCommit(file.getStorageKey()));
    }

    private FileAttachment requireFileAccess(UUID fileId, UUID callerUserId) {
        FileAttachment file = fileRepository.findById(fileId).orElseThrow(() -> new FileNotFoundApiException(fileId));
        try {
            taskService.getTask(file.getTaskId(), callerUserId);
        } catch (RuntimeException e) {
            // Not allowed to see the task => the file "does not exist" for this caller.
            throw new FileNotFoundApiException(fileId);
        }
        return file;
    }

    private void deleteObjectAfterCommit(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(storageKey);
                }
            });
        } else {
            deleteQuietly(storageKey);
        }
    }

    private void deleteQuietly(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException e) {
            log.warn("Could not delete stored object {}: {}", storageKey, e.getMessage());
        }
    }

    /** Keeps only the last path segment, drops control characters, and bounds the length. */
    static String sanitizeFilename(String raw) {
        String name = raw == null ? "" : raw;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.length() > 200) {
            String extension = FileTypeSniffer.extensionOf(name);
            name = name.substring(0, 190) + (extension.isEmpty() ? "" : "." + extension);
        }
        return name.isEmpty() ? "file" : name;
    }

    private static boolean declaredTypeAgrees(String declared, String detected) {
        if (declared == null || declared.isBlank()) {
            return true;
        }
        String base = declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return base.equals("application/octet-stream") || base.equals(detected);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}

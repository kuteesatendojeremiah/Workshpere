package com.worksphere.file;

import java.time.Instant;
import java.util.UUID;

/** What the API shows about a file. Deliberately omits the storage key and the checksum. */
public record FileResponse(
        UUID id,
        UUID taskId,
        String filename,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        Instant createdAt) {

    public static FileResponse from(FileAttachment file) {
        return new FileResponse(
                file.getId(),
                file.getTaskId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getUploadedBy(),
                file.getCreatedAt());
    }
}

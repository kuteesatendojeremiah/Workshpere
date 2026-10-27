package com.worksphere.file;

import com.worksphere.common.pagination.PageResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/api/v1/tasks/{taskId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID callerUserId,
            @RequestParam("file") MultipartFile file)
            throws IOException {
        return FileResponse.from(fileService.upload(
                taskId, callerUserId, file.getOriginalFilename(), file.getContentType(), file.getBytes()));
    }

    @GetMapping("/api/v1/tasks/{taskId}/attachments")
    public PageResponse<FileResponse> list(
            @PathVariable UUID taskId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(fileService.list(taskId, callerUserId, pageable).map(FileResponse::from));
    }

    /**
     * Streams the file through the API so authorization is checked on EVERY download (a long-lived
     * public storage URL would keep working after someone loses access). The headers stop a browser
     * from treating the bytes as a web page: forced download, the type we detected, and no sniffing.
     */
    @GetMapping("/api/v1/attachments/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable UUID fileId, @AuthenticationPrincipal UUID callerUserId) {
        FileService.Download download = fileService.open(fileId, callerUserId);
        InputStream content = download.content();
        FileAttachment file = download.file();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentDisposition(file.getOriginalFilename()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(content));
    }

    @DeleteMapping("/api/v1/attachments/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID fileId, @AuthenticationPrincipal UUID callerUserId) {
        fileService.delete(fileId, callerUserId);
    }

    private static String attachmentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(URLEncoder.encode(filename, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}

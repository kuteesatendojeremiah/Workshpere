package com.worksphere.file;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, UUID> {

    Page<FileAttachment> findAllByTaskId(UUID taskId, Pageable pageable);

    List<FileAttachment> findAllByTaskId(UUID taskId);

    long countByTaskId(UUID taskId);
}

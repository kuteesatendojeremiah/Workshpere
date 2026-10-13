package com.worksphere.task;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID taskId, UUID authorId, String authorDisplayName, String body, Instant createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTask().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getDisplayName(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}

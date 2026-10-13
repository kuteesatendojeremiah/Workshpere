package com.worksphere.task;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreateCommentRequest(
        @NotBlank(message = "body must not be blank") String body, List<UUID> mentionedUserIds) {

    public List<UUID> mentionedUserIdsOrEmpty() {
        return mentionedUserIds != null ? mentionedUserIds : List.of();
    }
}

package com.worksphere.task;

import com.worksphere.common.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/v1/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody CreateCommentRequest request) {
        return CommentResponse.from(commentService.addComment(
                taskId, callerUserId, request.body(), request.mentionedUserIdsOrEmpty()));
    }

    @GetMapping("/api/v1/tasks/{taskId}/comments")
    public PageResponse<CommentResponse> list(
            @PathVariable UUID taskId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(
                commentService.listComments(taskId, callerUserId, pageable).map(CommentResponse::from));
    }

    @DeleteMapping("/api/v1/tasks/{taskId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UUID callerUserId) {
        commentService.deleteComment(taskId, commentId, callerUserId);
    }
}

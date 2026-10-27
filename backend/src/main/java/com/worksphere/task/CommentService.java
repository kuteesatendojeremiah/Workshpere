package com.worksphere.task;

import com.worksphere.common.outbox.DomainEventType;
import com.worksphere.common.outbox.OutboxService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.notification.NotificationService;
import com.worksphere.notification.NotificationType;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import com.worksphere.project.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final String MANAGE_PROJECTS = "MANAGE_PROJECTS";

    private final CommentRepository commentRepository;
    private final CommentMentionRepository mentionRepository;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final PermissionChecker permissionChecker;
    private final OutboxService outboxService;
    private final NotificationService notificationService;

    public CommentService(
            CommentRepository commentRepository,
            CommentMentionRepository mentionRepository,
            TaskService taskService,
            ProjectService projectService,
            OrganizationService organizationService,
            UserService userService,
            PermissionChecker permissionChecker,
            NotificationService notificationService,
            OutboxService outboxService) {
        this.commentRepository = commentRepository;
        this.mentionRepository = mentionRepository;
        this.taskService = taskService;
        this.projectService = projectService;
        this.organizationService = organizationService;
        this.userService = userService;
        this.permissionChecker = permissionChecker;
        this.notificationService = notificationService;
        this.outboxService = outboxService;
    }

    @Transactional
    public Comment addComment(UUID taskId, UUID authorUserId, String body, List<UUID> mentionedUserIds) {
        Task task = taskService.requireTaskAccess(taskId, authorUserId);
        User author = userService.getProfile(authorUserId);
        Comment comment = commentRepository.save(new Comment(task, author, body));

        for (UUID mentionedUserId : mentionedUserIds) {
            if (!projectService.isProjectMember(task.getProject().getId(), mentionedUserId)) {
                throw new UserNotInProjectException(mentionedUserId);
            }
            User mentionedUser = userService.getProfile(mentionedUserId);
            mentionRepository.save(new CommentMention(comment, mentionedUser));

            if (!mentionedUserId.equals(authorUserId)) {
                notificationService.notify(
                        mentionedUserId,
                        NotificationType.MENTIONED,
                        "You were mentioned in a comment on task '" + task.getTitle() + "'",
                        "TASK",
                        task.getId());
            }
        }

        outboxService.record(
                DomainEventType.COMMENT_CREATED,
                "COMMENT",
                comment.getId(),
                task.getProject().getOrganization().getId(),
                java.util.Map.of("taskId", String.valueOf(taskId), "author", String.valueOf(authorUserId)));
        return comment;
    }

    @Transactional(readOnly = true)
    public Page<Comment> listComments(UUID taskId, UUID callerUserId, Pageable pageable) {
        taskService.requireTaskAccess(taskId, callerUserId);
        return commentRepository.findAllByTaskId(taskId, pageable);
    }

    @Transactional
    public void deleteComment(UUID taskId, UUID commentId, UUID callerUserId) {
        Task task = taskService.requireTaskAccess(taskId, callerUserId);
        Comment comment = commentRepository
                .findByIdAndTaskId(commentId, taskId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        boolean isAuthor = comment.getAuthor().getId().equals(callerUserId);
        if (!isAuthor) {
            OrganizationMember callerMembership = organizationService.requireMembership(
                    task.getProject().getOrganization().getId(), callerUserId);
            if (!permissionChecker.hasPermission(callerMembership.getRole(), MANAGE_PROJECTS)) {
                throw new MissingPermissionException(MANAGE_PROJECTS);
            }
        }

        commentRepository.delete(comment);
    }
}

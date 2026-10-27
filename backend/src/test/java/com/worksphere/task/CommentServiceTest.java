package com.worksphere.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.TestRoles;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.notification.NotificationService;
import com.worksphere.organization.Organization;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private com.worksphere.common.outbox.OutboxService outboxService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentMentionRepository mentionRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private ProjectService projectService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    private final PermissionChecker permissionChecker = new PermissionChecker();
    private final Role ownerRole = TestRoles.role("ORGANIZATION_OWNER", "MANAGE_PROJECTS");
    private final Role memberRole = TestRoles.role("EMPLOYEE_MEMBER");

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(
                commentRepository,
                mentionRepository,
                taskService,
                projectService,
                organizationService,
                userService,
                permissionChecker,
                notificationService, outboxService);
    }

    @Test
    void addCommentRejectsMentionOfNonProjectMember() {
        UUID taskId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID mentionedId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        User author = new User("author@example.com", "hash", "Author");
        when(taskService.requireTaskAccess(taskId, authorId)).thenReturn(task);
        when(userService.getProfile(authorId)).thenReturn(author);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectService.isProjectMember(project.getId(), mentionedId)).thenReturn(false);

        assertThatThrownBy(() ->
                        commentService.addComment(taskId, authorId, "Hey @someone", List.of(mentionedId)))
                .isInstanceOf(UserNotInProjectException.class);
    }

    @Test
    void addCommentStoresValidMentions() {
        UUID taskId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID mentionedId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        User author = new User("author@example.com", "hash", "Author");
        User mentioned = new User("mentioned@example.com", "hash", "Mentioned");
        when(taskService.requireTaskAccess(taskId, authorId)).thenReturn(task);
        when(userService.getProfile(authorId)).thenReturn(author);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectService.isProjectMember(project.getId(), mentionedId)).thenReturn(true);
        when(userService.getProfile(mentionedId)).thenReturn(mentioned);

        Comment comment = commentService.addComment(taskId, authorId, "Hey @mentioned", List.of(mentionedId));

        assertThat(comment.getBody()).isEqualTo("Hey @mentioned");
        org.mockito.Mockito.verify(mentionRepository).save(any(CommentMention.class));
    }

    @Test
    void authorCanDeleteOwnComment() {
        UUID taskId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        User author = new User("author@example.com", "hash", "Author");
        setUserId(author, authorId);
        Comment comment = new Comment(task, author, "Hello");
        when(taskService.requireTaskAccess(taskId, authorId)).thenReturn(task);
        when(commentRepository.findByIdAndTaskId(commentId, taskId)).thenReturn(Optional.of(comment));

        commentService.deleteComment(taskId, commentId, authorId);

        org.mockito.Mockito.verify(commentRepository).delete(comment);
    }

    @Test
    void nonAuthorWithoutManagePermissionCannotDelete() {
        UUID taskId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project project = new Project(organization, "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        User author = new User("author@example.com", "hash", "Author");
        setUserId(author, authorId);
        Comment comment = new Comment(task, author, "Hello");
        User caller = new User("caller@example.com", "hash", "Caller");
        OrganizationMember callerMembership = new OrganizationMember(organization, caller, memberRole);
        when(taskService.requireTaskAccess(taskId, callerId)).thenReturn(task);
        when(commentRepository.findByIdAndTaskId(commentId, taskId)).thenReturn(Optional.of(comment));
        when(organizationService.requireMembership(organization.getId(), callerId)).thenReturn(callerMembership);

        assertThatThrownBy(() -> commentService.deleteComment(taskId, commentId, callerId))
                .isInstanceOf(MissingPermissionException.class);
    }

    private void setUserId(User user, UUID id) {
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
    }
}

package com.worksphere.issue;

import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.notification.NotificationService;
import com.worksphere.notification.NotificationType;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectNotFoundException;
import com.worksphere.project.ProjectService;
import com.worksphere.task.Task;
import com.worksphere.task.TaskService;
import com.worksphere.task.UserNotInProjectException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final UserService userService;
    private final NotificationService notificationService;

    public IssueService(
            IssueRepository issueRepository,
            ProjectService projectService,
            TaskService taskService,
            UserService userService,
            NotificationService notificationService) {
        this.issueRepository = issueRepository;
        this.projectService = projectService;
        this.taskService = taskService;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Issue reportIssue(
            UUID projectId,
            UUID reporterUserId,
            String title,
            String description,
            IssueSeverity severity,
            IssuePriority priority,
            IssueEnvironment environment) {
        Project project = projectService.requireProjectAccess(projectId, reporterUserId);
        User reporter = userService.getProfile(reporterUserId);
        return issueRepository.save(
                new Issue(project, title, description, severity, priority, environment, reporter));
    }

    @Transactional(readOnly = true)
    public Issue getIssue(UUID issueId, UUID callerUserId) {
        return requireIssueAccess(issueId, callerUserId);
    }

    @Transactional(readOnly = true)
    public Page<Issue> listIssues(UUID projectId, UUID callerUserId, IssueFilter filter, Pageable pageable) {
        projectService.requireProjectAccess(projectId, callerUserId);

        var spec = IssueSpecifications.inProject(projectId)
                .and(IssueSpecifications.hasSeverity(filter.severity()))
                .and(IssueSpecifications.hasPriority(filter.priority()))
                .and(IssueSpecifications.hasEnvironment(filter.environment()))
                .and(IssueSpecifications.hasStatus(filter.status()));
        return issueRepository.findAll(spec, pageable);
    }

    @Transactional
    public Issue updateIssue(
            UUID issueId,
            UUID callerUserId,
            String title,
            String description,
            IssueSeverity severity,
            IssuePriority priority,
            IssueEnvironment environment) {
        Issue issue = requireIssueAccess(issueId, callerUserId);
        issue.updateDetails(title, description, severity, priority, environment);
        return issue;
    }

    @Transactional
    public Issue changeStatus(UUID issueId, UUID callerUserId, IssueStatus status) {
        Issue issue = requireIssueAccess(issueId, callerUserId);
        issue.changeStatus(status);
        return issue;
    }

    @Transactional
    public Issue assignIssue(UUID issueId, UUID callerUserId, UUID assigneeId) {
        Issue issue = requireIssueAccess(issueId, callerUserId);

        if (assigneeId == null) {
            issue.assignTo(null);
            return issue;
        }

        if (!projectService.isProjectMember(issue.getProject().getId(), assigneeId)) {
            throw new UserNotInProjectException(assigneeId);
        }

        issue.assignTo(userService.getProfile(assigneeId));

        if (!assigneeId.equals(callerUserId)) {
            notificationService.notify(
                    assigneeId,
                    NotificationType.ISSUE_ASSIGNED,
                    "You were assigned to issue '" + issue.getTitle() + "'",
                    "ISSUE",
                    issue.getId());
        }
        return issue;
    }

    @Transactional
    public Issue linkTask(UUID issueId, UUID callerUserId, UUID taskId) {
        Issue issue = requireIssueAccess(issueId, callerUserId);

        Task task = taskService.getTask(taskId, callerUserId);
        if (!task.getProject().getId().equals(issue.getProject().getId())) {
            throw new InvalidLinkedTaskException();
        }

        issue.linkToTask(task);
        return issue;
    }

    @Transactional
    public Issue unlinkTask(UUID issueId, UUID callerUserId) {
        Issue issue = requireIssueAccess(issueId, callerUserId);
        issue.linkToTask(null);
        return issue;
    }

    private Issue requireIssueAccess(UUID issueId, UUID callerUserId) {
        Issue issue = issueRepository.findById(issueId).orElseThrow(() -> new IssueNotFoundException(issueId));

        try {
            projectService.requireProjectAccess(issue.getProject().getId(), callerUserId);
        } catch (ProjectNotFoundException e) {
            throw new IssueNotFoundException(issueId);
        }

        return issue;
    }
}

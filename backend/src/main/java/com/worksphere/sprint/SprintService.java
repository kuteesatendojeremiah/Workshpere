package com.worksphere.sprint;

import com.worksphere.common.outbox.DomainEventType;
import com.worksphere.common.outbox.OutboxService;
import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectNotFoundException;
import com.worksphere.project.ProjectService;
import com.worksphere.task.Task;
import com.worksphere.task.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SprintService {

    private static final String MANAGE_SPRINTS = "MANAGE_SPRINTS";

    private final SprintRepository sprintRepository;
    private final SprintTaskRepository sprintTaskRepository;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final OrganizationService organizationService;
    private final PermissionChecker permissionChecker;
    private final AuditService auditService;
    private final OutboxService outboxService;

    public SprintService(
            SprintRepository sprintRepository,
            SprintTaskRepository sprintTaskRepository,
            ProjectService projectService,
            TaskService taskService,
            OrganizationService organizationService,
            PermissionChecker permissionChecker,
            AuditService auditService,
            OutboxService outboxService) {
        this.sprintRepository = sprintRepository;
        this.sprintTaskRepository = sprintTaskRepository;
        this.projectService = projectService;
        this.taskService = taskService;
        this.organizationService = organizationService;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
        this.outboxService = outboxService;
    }

    @Transactional
    public Sprint createSprint(
            UUID projectId, UUID callerUserId, String name, String goal, LocalDate startDate, LocalDate endDate) {
        Project project = projectService.requireProjectAccess(projectId, callerUserId);
        requireManageSprints(project, callerUserId);

        if (endDate.isBefore(startDate)) {
            throw new InvalidSprintStateException("endDate must not be before startDate");
        }

        return sprintRepository.save(new Sprint(project, name, goal, startDate, endDate));
    }

    @Transactional(readOnly = true)
    public Sprint getSprint(UUID sprintId, UUID callerUserId) {
        return requireSprintAccess(sprintId, callerUserId);
    }

    @Transactional(readOnly = true)
    public Page<Sprint> listSprints(UUID projectId, UUID callerUserId, Pageable pageable) {
        projectService.requireProjectAccess(projectId, callerUserId);
        return sprintRepository.findAllByProjectId(projectId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Task> listBacklog(UUID projectId, UUID callerUserId, Pageable pageable) {
        List<UUID> plannedOrActiveTaskIds = sprintTaskRepository
                .findAllBySprint_Project_IdAndSprint_StatusIn(
                        projectId, List.of(SprintStatus.PLANNED, SprintStatus.ACTIVE))
                .stream()
                .map(sprintTask -> sprintTask.getTask().getId())
                .distinct()
                .toList();

        return taskService.listTasksExcluding(projectId, callerUserId, plannedOrActiveTaskIds, pageable);
    }

    @Transactional
    public Sprint startSprint(UUID sprintId, UUID callerUserId) {
        Sprint sprint = requireSprintAccess(sprintId, callerUserId);
        requireManageSprints(sprint.getProject(), callerUserId);

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new InvalidSprintStateException("Only a PLANNED sprint can be started");
        }
        if (sprintRepository.existsByProject_IdAndStatus(sprint.getProject().getId(), SprintStatus.ACTIVE)) {
            throw new InvalidSprintStateException("This project already has an active sprint");
        }

        sprint.start();
        auditService.record(
                sprint.getProject().getOrganization().getId(),
                callerUserId,
                AuditAction.SPRINT_STARTED,
                AuditResourceType.SPRINT,
                sprintId,
                null);
        outboxService.record(
                DomainEventType.SPRINT_STARTED,
                "SPRINT",
                sprintId,
                sprint.getProject().getOrganization().getId(),
                java.util.Map.of("projectId", String.valueOf(sprint.getProject().getId())));
        return sprint;
    }

    @Transactional
    public Sprint completeSprint(UUID sprintId, UUID callerUserId) {
        Sprint sprint = requireSprintAccess(sprintId, callerUserId);
        requireManageSprints(sprint.getProject(), callerUserId);

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new InvalidSprintStateException("Only an ACTIVE sprint can be completed");
        }

        sprint.complete();
        auditService.record(
                sprint.getProject().getOrganization().getId(),
                callerUserId,
                AuditAction.SPRINT_COMPLETED,
                AuditResourceType.SPRINT,
                sprintId,
                null);
        outboxService.record(
                DomainEventType.SPRINT_COMPLETED,
                "SPRINT",
                sprintId,
                sprint.getProject().getOrganization().getId(),
                java.util.Map.of("projectId", String.valueOf(sprint.getProject().getId())));
        return sprint;
    }

    @Transactional
    public SprintTask addTaskToSprint(UUID sprintId, UUID callerUserId, UUID taskId, int storyPoints) {
        Sprint sprint = requireSprintAccess(sprintId, callerUserId);
        requireManageSprints(sprint.getProject(), callerUserId);

        Task task = taskService.getTask(taskId, callerUserId);
        if (!task.getProject().getId().equals(sprint.getProject().getId())) {
            throw new InvalidSprintTaskException("The task must belong to the same project as the sprint");
        }

        return sprintTaskRepository
                .findBySprintIdAndTaskId(sprintId, taskId)
                .orElseGet(() -> sprintTaskRepository.save(new SprintTask(sprint, task, storyPoints)));
    }

    @Transactional
    public void removeTaskFromSprint(UUID sprintId, UUID callerUserId, UUID taskId) {
        Sprint sprint = requireSprintAccess(sprintId, callerUserId);
        requireManageSprints(sprint.getProject(), callerUserId);

        sprintTaskRepository.deleteBySprintIdAndTaskId(sprintId, taskId);
    }

    @Transactional(readOnly = true)
    public List<SprintTask> listSprintTasks(UUID sprintId, UUID callerUserId) {
        requireSprintAccess(sprintId, callerUserId);
        return sprintTaskRepository.findAllBySprintId(sprintId);
    }

    @Transactional(readOnly = true)
    public VelocityResult getVelocity(UUID sprintId, UUID callerUserId) {
        requireSprintAccess(sprintId, callerUserId);
        List<SprintTask> sprintTasks = sprintTaskRepository.findAllBySprintId(sprintId);

        int committed = sprintTasks.stream().mapToInt(SprintTask::getStoryPoints).sum();
        int completed = sprintTasks.stream()
                .filter(st -> st.getTask().getCompletedAt() != null)
                .mapToInt(SprintTask::getStoryPoints)
                .sum();

        return new VelocityResult(committed, completed);
    }

    @Transactional(readOnly = true)
    public List<BurndownPoint> getBurndown(UUID sprintId, UUID callerUserId) {
        Sprint sprint = requireSprintAccess(sprintId, callerUserId);
        List<SprintTask> sprintTasks = sprintTaskRepository.findAllBySprintId(sprintId);

        LocalDate today = LocalDate.now();
        LocalDate lastDay = sprint.getStatus() == SprintStatus.COMPLETED || today.isAfter(sprint.getEndDate())
                ? sprint.getEndDate()
                : today;

        List<BurndownPoint> points = new ArrayList<>();
        for (LocalDate day = sprint.getStartDate(); !day.isAfter(lastDay); day = day.plusDays(1)) {
            Instant endOfDay = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            int remaining = sprintTasks.stream()
                    .filter(st -> st.getTask().getCompletedAt() == null
                            || st.getTask().getCompletedAt().isAfter(endOfDay))
                    .mapToInt(SprintTask::getStoryPoints)
                    .sum();
            points.add(new BurndownPoint(day, remaining));
        }
        return points;
    }

    private Sprint requireSprintAccess(UUID sprintId, UUID callerUserId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new SprintNotFoundException(sprintId));

        try {
            projectService.requireProjectAccess(sprint.getProject().getId(), callerUserId);
        } catch (ProjectNotFoundException e) {
            throw new SprintNotFoundException(sprintId);
        }

        return sprint;
    }

    private void requireManageSprints(Project project, UUID callerUserId) {
        OrganizationMember callerMembership =
                organizationService.requireMembership(project.getOrganization().getId(), callerUserId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_SPRINTS);
    }
}

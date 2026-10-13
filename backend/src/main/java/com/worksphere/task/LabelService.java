package com.worksphere.task;

import com.worksphere.project.Project;
import com.worksphere.project.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final ProjectService projectService;
    private final TaskService taskService;

    public LabelService(
            LabelRepository labelRepository,
            TaskLabelRepository taskLabelRepository,
            ProjectService projectService,
            TaskService taskService) {
        this.labelRepository = labelRepository;
        this.taskLabelRepository = taskLabelRepository;
        this.projectService = projectService;
        this.taskService = taskService;
    }

    @Transactional
    public Label createLabel(UUID projectId, UUID callerUserId, String name, String color) {
        Project project = projectService.requireProjectAccess(projectId, callerUserId);
        if (labelRepository.existsByProjectIdAndName(projectId, name)) {
            throw new DuplicateLabelNameException(name);
        }

        return labelRepository.save(new Label(project, name, color));
    }

    @Transactional(readOnly = true)
    public List<Label> listLabels(UUID projectId, UUID callerUserId) {
        projectService.requireProjectAccess(projectId, callerUserId);
        return labelRepository.findAllByProjectId(projectId);
    }

    @Transactional
    public void deleteLabel(UUID projectId, UUID labelId, UUID callerUserId) {
        projectService.requireProjectAccess(projectId, callerUserId);
        Label label = labelRepository
                .findByIdAndProjectId(labelId, projectId)
                .orElseThrow(() -> new LabelNotFoundException(labelId));

        labelRepository.delete(label);
    }

    @Transactional
    public TaskLabel attachLabel(UUID taskId, UUID callerUserId, UUID labelId) {
        Task task = taskService.requireTaskAccess(taskId, callerUserId);
        Label label = labelRepository
                .findByIdAndProjectId(labelId, task.getProject().getId())
                .orElseThrow(() -> new LabelNotFoundException(labelId));

        return taskLabelRepository
                .findByTaskIdAndLabelId(taskId, labelId)
                .orElseGet(() -> taskLabelRepository.save(new TaskLabel(task, label)));
    }

    @Transactional
    public void detachLabel(UUID taskId, UUID callerUserId, UUID labelId) {
        taskService.requireTaskAccess(taskId, callerUserId);
        taskLabelRepository.deleteByTaskIdAndLabelId(taskId, labelId);
    }

    @Transactional(readOnly = true)
    public List<TaskLabel> listTaskLabels(UUID taskId, UUID callerUserId) {
        taskService.requireTaskAccess(taskId, callerUserId);
        return taskLabelRepository.findAllByTaskId(taskId);
    }
}

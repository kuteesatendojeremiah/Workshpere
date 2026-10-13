package com.worksphere.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.organization.Organization;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private TaskLabelRepository taskLabelRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private TaskService taskService;

    private LabelService labelService;

    @BeforeEach
    void setUp() {
        labelService = new LabelService(labelRepository, taskLabelRepository, projectService, taskService);
    }

    @Test
    void createLabelRejectsDuplicateName() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        when(projectService.requireProjectAccess(projectId, callerId)).thenReturn(project);
        when(labelRepository.existsByProjectIdAndName(projectId, "Bug")).thenReturn(true);

        assertThatThrownBy(() -> labelService.createLabel(projectId, callerId, "Bug", "#FF0000"))
                .isInstanceOf(DuplicateLabelNameException.class);
    }

    @Test
    void attachingLabelTwiceIsIdempotent() {
        UUID taskId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        Label label = new Label(project, "Bug", "#FF0000");
        TaskLabel existing = new TaskLabel(task, label);
        when(taskService.requireTaskAccess(taskId, callerId)).thenReturn(task);
        when(labelRepository.findByIdAndProjectId(labelId, project.getId())).thenReturn(Optional.of(label));
        when(taskLabelRepository.findByTaskIdAndLabelId(taskId, labelId)).thenReturn(Optional.of(existing));

        TaskLabel result = labelService.attachLabel(taskId, callerId, labelId);

        assertThat(result).isEqualTo(existing);
        org.mockito.Mockito.verify(taskLabelRepository, org.mockito.Mockito.never()).save(any(TaskLabel.class));
    }

    @Test
    void attachingLabelFromDifferentProjectFails() {
        UUID taskId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        when(taskService.requireTaskAccess(taskId, callerId)).thenReturn(task);
        when(labelRepository.findByIdAndProjectId(labelId, project.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.attachLabel(taskId, callerId, labelId))
                .isInstanceOf(LabelNotFoundException.class);
    }
}

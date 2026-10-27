package com.worksphere.demo;

import com.worksphere.identity.EmailAlreadyRegisteredException;
import com.worksphere.identity.RegisterRequest;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.organization.Organization;
import com.worksphere.organization.OrganizationService;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectService;
import com.worksphere.sprint.SprintService;
import com.worksphere.task.Task;
import com.worksphere.task.TaskPriority;
import com.worksphere.task.TaskService;
import com.worksphere.task.TaskStatus;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fills an empty development database with a believable demo organization so the app can be shown
 * straight after {@code docker compose up} (spec section 26: "seed data should be available for demos").
 *
 * <p>Safe by construction:
 * <ul>
 *   <li>Off unless {@code app.seed.demo=true}, and refused when {@code app.environment=production}
 *       (see {@code ProductionConfigGuard}): the demo account has a publicly documented password.
 *   <li>It goes through the same services as the API, so every rule (plan limits, audit, events) applies.
 *   <li>Idempotent: if the demo user already exists nothing is created.
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo.owner@worksphere.local";
    public static final String DEMO_PASSWORD = "Demo-Passw0rd!";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserService userService;
    private final OrganizationService organizationService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final SprintService sprintService;

    public DemoDataSeeder(
            UserService userService,
            OrganizationService organizationService,
            ProjectService projectService,
            TaskService taskService,
            SprintService sprintService) {
        this.userService = userService;
        this.organizationService = organizationService;
        this.projectService = projectService;
        this.taskService = taskService;
        this.sprintService = sprintService;
    }

    @Override
    public void run(ApplicationArguments args) {
        User owner;
        try {
            owner = userService.register(new RegisterRequest(DEMO_EMAIL, DEMO_PASSWORD, "Demo Owner"));
        } catch (EmailAlreadyRegisteredException e) {
            log.info("Demo data already present, not seeding again");
            return;
        }

        Organization organization = organizationService.createOrganization(owner.getId(), "Acme Demo");
        Project website = projectService.createProject(
                organization.getId(), owner.getId(), "Website relaunch", "Rebuild the marketing site");
        Project mobile = projectService.createProject(
                organization.getId(), owner.getId(), "Mobile app", "First release of the companion app");

        task(website, owner, "Write the new homepage copy", TaskPriority.HIGH, TaskStatus.DONE);
        task(website, owner, "Design the pricing page", TaskPriority.HIGH, TaskStatus.IN_REVIEW);
        task(website, owner, "Set up analytics", TaskPriority.MEDIUM, TaskStatus.IN_PROGRESS);
        task(website, owner, "Fix broken links in the footer", TaskPriority.LOW, TaskStatus.TODO);
        task(website, owner, "Translate the site into French", TaskPriority.MEDIUM, TaskStatus.TODO);
        task(mobile, owner, "Choose a push-notification provider", TaskPriority.MEDIUM, TaskStatus.IN_PROGRESS);
        task(mobile, owner, "Build the login screen", TaskPriority.HIGH, TaskStatus.TODO);
        task(mobile, owner, "Draft the app-store listing", TaskPriority.LOW, TaskStatus.TODO);

        LocalDate today = LocalDate.now();
        sprintService.createSprint(
                website.getId(), owner.getId(), "Sprint 1", "Ship the new homepage", today, today.plusDays(13));

        log.info("Seeded demo data. Sign in as {} (see docs/operations/docker.md for the password)", DEMO_EMAIL);
    }

    /** Creates a task and walks it forward one column at a time until it reaches {@code target}. */
    private void task(Project project, User owner, String title, TaskPriority priority, TaskStatus target) {
        Task task = taskService.createTask(project.getId(), owner.getId(), title, null, priority, null);
        for (TaskStatus next : TaskStatus.values()) {
            if (next.ordinal() > task.getStatus().ordinal() && next.ordinal() <= target.ordinal()) {
                task = taskService.changeStatus(task.getId(), owner.getId(), next, null, task.getVersion());
            }
        }
    }
}

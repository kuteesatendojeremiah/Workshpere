package com.worksphere.project;

import com.worksphere.common.realtime.RealtimeTopicAuthorizer;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Only people who can already see a project may listen to its board's live events. */
@Component
public class ProjectBoardTopicAuthorizer implements RealtimeTopicAuthorizer {

    private static final Pattern BOARD_TOPIC = Pattern.compile("^/topic/projects/([0-9a-fA-F-]{36})/board$");

    private final ProjectService projectService;

    public ProjectBoardTopicAuthorizer(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public boolean supports(String destination) {
        return destination.startsWith("/topic/projects/");
    }

    @Override
    public boolean canSubscribe(UUID userId, String destination) {
        Matcher matcher = BOARD_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return false;
        }
        try {
            projectService.requireProjectAccess(UUID.fromString(matcher.group(1)), userId);
            return true;
        } catch (RuntimeException e) {
            return false; // not found / not a member / no visibility: all look the same to the caller
        }
    }
}

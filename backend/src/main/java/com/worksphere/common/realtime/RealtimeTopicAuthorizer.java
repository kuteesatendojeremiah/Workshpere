package com.worksphere.common.realtime;

import java.util.UUID;

/**
 * Decides who may subscribe to a family of WebSocket destinations. The common package defines
 * this interface; the module that owns the data (e.g. project) implements it. That keeps
 * {@code common} free of dependencies on feature modules, and keeps the authorization rule next
 * to the code that already knows what "access to this project" means.
 */
public interface RealtimeTopicAuthorizer {

    /** True if this authorizer is responsible for the destination (e.g. it matches a prefix). */
    boolean supports(String destination);

    /** Called only when {@link #supports} is true. Must be a real server-side check. */
    boolean canSubscribe(UUID userId, String destination);
}

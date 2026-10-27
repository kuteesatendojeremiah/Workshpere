package com.worksphere.task;

import java.util.UUID;

/**
 * Published (synchronously, inside the deleting transaction) just before a task is deleted, so other
 * modules can clean up what belongs to it, e.g. the file module removes attachments. An event keeps
 * the dependency pointing the right way: file knows about task, task does not know about file.
 */
public record TaskDeletedEvent(UUID taskId) {
}

package com.worksphere.task;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE;

    /**
     * Forward moves must happen one step at a time (no skipping a column); moving backward to
     * any earlier status is always allowed (reopening a task, sending it back from review, ...).
     */
    public boolean canTransitionTo(TaskStatus target) {
        if (target == this) {
            return true;
        }
        if (target.ordinal() < this.ordinal()) {
            return true;
        }
        return target.ordinal() == this.ordinal() + 1;
    }
}

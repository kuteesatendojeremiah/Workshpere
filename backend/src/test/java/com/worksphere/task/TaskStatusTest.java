package com.worksphere.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskStatusTest {

    @ParameterizedTest
    @CsvSource({
        "TODO, TODO, true", // no-op move (pure reorder within the same column)
        "TODO, IN_PROGRESS, true", // one step forward
        "TODO, IN_REVIEW, false", // skips a column forward
        "TODO, DONE, false", // skips two columns forward
        "IN_PROGRESS, IN_REVIEW, true",
        "IN_REVIEW, DONE, true",
        "IN_PROGRESS, TODO, true", // backward is always fine
        "DONE, TODO, true",
        "DONE, IN_PROGRESS, true",
        "IN_REVIEW, TODO, true"
    })
    void transitionRules(TaskStatus from, TaskStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }
}

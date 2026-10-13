package com.worksphere.task;

import java.util.UUID;

public record LabelResponse(UUID id, UUID projectId, String name, String color) {

    public static LabelResponse from(Label label) {
        return new LabelResponse(label.getId(), label.getProject().getId(), label.getName(), label.getColor());
    }
}

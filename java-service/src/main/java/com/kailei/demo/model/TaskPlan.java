package com.kailei.demo.model;

import java.time.OffsetDateTime;
import java.util.List;

public record TaskPlan(
        String planId,
        String traceId,
        String sessionId,
        String sourceText,
        String userId,
        TaskStatus status,
        List<TaskAction> tasks,
        List<String> warnings,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public TaskPlan withStatus(TaskStatus nextStatus, List<TaskAction> nextTasks) {
        return new TaskPlan(
                planId,
                traceId,
                sessionId,
                sourceText,
                userId,
                nextStatus,
                List.copyOf(nextTasks),
                warnings,
                createdAt,
                OffsetDateTime.now()
        );
    }
}

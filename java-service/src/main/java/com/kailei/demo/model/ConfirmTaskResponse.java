package com.kailei.demo.model;

public record ConfirmTaskResponse(
        String planId,
        TaskStatus status,
        ExecutionSummary executionSummary,
        TaskPlan plan
) {
}

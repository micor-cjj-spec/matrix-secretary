package com.kailei.demo.model;

import com.kailei.demo.entity.TaskDispatchRecordEntity;

import java.time.OffsetDateTime;

public record TaskDispatchRecordResponse(
        String id,
        String planId,
        String actionId,
        OffsetDateTime triggerAt,
        String status,
        String dispatchOwner,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Integer retryCount,
        Integer maxRetryCount,
        OffsetDateTime nextRetryAt,
        String errorMessage,
        String idempotencyKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TaskDispatchRecordResponse from(TaskDispatchRecordEntity entity) {
        return new TaskDispatchRecordResponse(
                entity.getId(),
                entity.getPlanId(),
                entity.getActionId(),
                entity.getTriggerAt(),
                entity.getStatus(),
                entity.getDispatchOwner(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getRetryCount(),
                entity.getMaxRetryCount(),
                entity.getNextRetryAt(),
                entity.getErrorMessage(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

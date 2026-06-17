package com.kailei.demo.model;

import com.kailei.demo.entity.TaskExecutionLogEntity;

import java.time.OffsetDateTime;

public record TaskExecutionLogResponse(
        String id,
        String planId,
        String actionId,
        String skillName,
        String status,
        String requestPayload,
        String responsePayload,
        String errorMessage,
        String operatorUserId,
        OffsetDateTime createdAt
) {
    public static TaskExecutionLogResponse from(TaskExecutionLogEntity entity) {
        return new TaskExecutionLogResponse(
                entity.getId(),
                entity.getPlanId(),
                entity.getActionId(),
                entity.getSkillName(),
                entity.getStatus(),
                entity.getRequestPayload(),
                entity.getResponsePayload(),
                entity.getErrorMessage(),
                entity.getOperatorUserId(),
                entity.getCreatedAt()
        );
    }
}

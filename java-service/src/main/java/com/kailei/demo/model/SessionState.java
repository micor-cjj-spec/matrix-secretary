package com.kailei.demo.model;

import java.time.OffsetDateTime;

public record SessionState(
        String sessionId,
        String userId,
        String lastPlanId,
        String pendingActionId,
        String status,
        String lastUserInput,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

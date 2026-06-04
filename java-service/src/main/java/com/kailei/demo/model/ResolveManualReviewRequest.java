package com.kailei.demo.model;

import java.util.Map;

public record ResolveManualReviewRequest(
        String operatorUserId,
        String title,
        String content,
        TaskTarget target,
        TaskSchedule schedule,
        Map<String, Object> args,
        String priority,
        Boolean requiresConfirmation,
        Boolean executeNow,
        String note
) {
}

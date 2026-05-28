package com.kailei.demo.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record TaskSchedule(
        @JsonAlias("schedule_type")
        String scheduleType,
        @JsonAlias("original_text")
        String originalText,
        @JsonAlias("run_at")
        String runAt,
        String cron,
        String timezone
) {
    public boolean shouldRunImmediately() {
        return scheduleType == null || "none".equalsIgnoreCase(scheduleType);
    }

    public boolean isScheduled() {
        return "once".equalsIgnoreCase(scheduleType) || "recurring".equalsIgnoreCase(scheduleType);
    }
}

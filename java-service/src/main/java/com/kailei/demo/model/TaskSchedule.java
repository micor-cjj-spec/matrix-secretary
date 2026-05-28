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
        String timezone,
        @JsonAlias("next_run_at")
        String nextRunAt,
        @JsonAlias("last_run_at")
        String lastRunAt,
        @JsonAlias("trigger_count")
        Integer triggerCount
) {
    public TaskSchedule {
        scheduleType = scheduleType == null || scheduleType.isBlank() ? "none" : scheduleType;
        timezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone;
        triggerCount = triggerCount == null ? 0 : triggerCount;
    }

    public boolean shouldRunImmediately() {
        return "none".equalsIgnoreCase(scheduleType);
    }

    public boolean isScheduled() {
        return "once".equalsIgnoreCase(scheduleType) || "recurring".equalsIgnoreCase(scheduleType);
    }

    public boolean isOnce() {
        return "once".equalsIgnoreCase(scheduleType);
    }

    public boolean isRecurring() {
        return "recurring".equalsIgnoreCase(scheduleType);
    }

    public String effectiveRunAt() {
        return nextRunAt != null && !nextRunAt.isBlank() ? nextRunAt : runAt;
    }

    public TaskSchedule withCron(String nextCron) {
        return new TaskSchedule(scheduleType, originalText, runAt, nextCron, timezone, nextRunAt, lastRunAt, triggerCount);
    }

    public TaskSchedule withNextRunAt(String nextRunAtValue) {
        return new TaskSchedule(scheduleType, originalText, runAt, cron, timezone, nextRunAtValue, lastRunAt, triggerCount);
    }

    public TaskSchedule markTriggered(String triggeredAt, String nextRunAtValue) {
        return new TaskSchedule(scheduleType, originalText, runAt, cron, timezone, nextRunAtValue, triggeredAt, triggerCount + 1);
    }
}

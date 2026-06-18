package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class DispatchRetryPolicy {

    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int MAX_RETRY_BACKOFF_MINUTES = 30;

    public boolean shouldRetry(Integer retryCount, Integer maxRetryCount) {
        return valueOrDefault(retryCount, 0) < valueOrDefault(maxRetryCount, DEFAULT_MAX_RETRY_COUNT);
    }

    public TaskAction scheduleRetry(TaskAction failedAction, Integer retryCount, OffsetDateTime now) {
        int nextRetryCount = valueOrDefault(retryCount, 0) + 1;
        int backoffMinutes = retryBackoffMinutes(nextRetryCount);
        OffsetDateTime retryAt = now.plusMinutes(backoffMinutes);
        TaskSchedule currentSchedule = failedAction.schedule() == null
                ? new TaskSchedule("once", "retry", retryAt.toString(), null, "Asia/Shanghai", retryAt.toString(), null, 0)
                : failedAction.schedule();
        TaskSchedule retrySchedule = currentSchedule.withNextRunAt(retryAt.toString());
        String note = "任务执行失败，已安排第 " + nextRetryCount + " 次重试，retryAt=" + retryAt
                + ", reason=" + failedAction.executionNote();
        return failedAction.withSchedule(retrySchedule).withStatus(TaskStatus.SCHEDULED, note);
    }

    public int retryBackoffMinutes(int retryCount) {
        int minutes = 1;
        for (int i = 1; i < retryCount; i++) {
            minutes = Math.min(minutes * 2, MAX_RETRY_BACKOFF_MINUTES);
        }
        return minutes;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}

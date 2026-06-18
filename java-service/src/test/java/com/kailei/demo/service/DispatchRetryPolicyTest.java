package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.model.TaskTarget;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchRetryPolicyTest {

    private final DispatchRetryPolicy policy = new DispatchRetryPolicy();

    @Test
    void shouldRetryWhenRetryCountIsBelowDefaultLimit() {
        assertTrue(policy.shouldRetry(null, null));
        assertTrue(policy.shouldRetry(0, null));
        assertTrue(policy.shouldRetry(2, null));
        assertFalse(policy.shouldRetry(3, null));
    }

    @Test
    void shouldRetryWhenRetryCountIsBelowCustomLimit() {
        assertTrue(policy.shouldRetry(4, 5));
        assertFalse(policy.shouldRetry(5, 5));
    }

    @Test
    void retryBackoffUsesExponentialDelayWithCap() {
        assertEquals(1, policy.retryBackoffMinutes(1));
        assertEquals(2, policy.retryBackoffMinutes(2));
        assertEquals(4, policy.retryBackoffMinutes(3));
        assertEquals(8, policy.retryBackoffMinutes(4));
        assertEquals(16, policy.retryBackoffMinutes(5));
        assertEquals(30, policy.retryBackoffMinutes(6));
        assertEquals(30, policy.retryBackoffMinutes(10));
    }

    @Test
    void scheduleRetryMovesFailedActionBackToScheduled() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-18T12:00:00+08:00");
        TaskAction failedAction = action(TaskStatus.FAILED, new TaskSchedule(
                "once",
                "稍后提醒",
                "2026-06-18T12:00:00+08:00",
                null,
                "Asia/Shanghai",
                "2026-06-18T12:00:00+08:00",
                null,
                0
        ));

        TaskAction retryAction = policy.scheduleRetry(failedAction, 1, now);

        assertEquals(TaskStatus.SCHEDULED, retryAction.status());
        assertEquals("2026-06-18T12:02+08:00", retryAction.schedule().nextRunAt());
        assertTrue(retryAction.executionNote().contains("第 2 次重试"));
    }

    @Test
    void scheduleRetryCreatesScheduleWhenMissing() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-18T12:00:00+08:00");
        TaskAction failedAction = action(TaskStatus.FAILED, null);

        TaskAction retryAction = policy.scheduleRetry(failedAction, 0, now);

        assertEquals(TaskStatus.SCHEDULED, retryAction.status());
        assertEquals("once", retryAction.schedule().scheduleType());
        assertEquals("2026-06-18T12:01+08:00", retryAction.schedule().nextRunAt());
    }

    private TaskAction action(TaskStatus status, TaskSchedule schedule) {
        return new TaskAction(
                "action-test",
                "reminder",
                "reminder",
                "测试任务",
                "测试内容",
                new TaskTarget("self", "我", null),
                schedule,
                Map.of(),
                "normal",
                "MEDIUM",
                0.9,
                false,
                "测试输入",
                "测试解析备注",
                status,
                "测试失败"
        );
    }
}

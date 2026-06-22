package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.model.TaskTarget;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchRetryPolicyTest {

    private final DispatchRetryPolicy retryPolicy = new DispatchRetryPolicy();

    @Test
    void shouldRetryUsesDefaultMaxRetryCountWhenMissing() {
        assertThat(retryPolicy.shouldRetry(null, null)).isTrue();
        assertThat(retryPolicy.shouldRetry(2, null)).isTrue();
        assertThat(retryPolicy.shouldRetry(3, null)).isFalse();
    }

    @Test
    void shouldRetryRespectsExplicitMaxRetryCount() {
        assertThat(retryPolicy.shouldRetry(0, 1)).isTrue();
        assertThat(retryPolicy.shouldRetry(1, 1)).isFalse();
        assertThat(retryPolicy.shouldRetry(2, 2)).isFalse();
    }

    @Test
    void retryBackoffUsesExponentialBackoffWithUpperBound() {
        assertThat(retryPolicy.retryBackoffMinutes(1)).isEqualTo(1);
        assertThat(retryPolicy.retryBackoffMinutes(2)).isEqualTo(2);
        assertThat(retryPolicy.retryBackoffMinutes(3)).isEqualTo(4);
        assertThat(retryPolicy.retryBackoffMinutes(6)).isEqualTo(30);
        assertThat(retryPolicy.retryBackoffMinutes(10)).isEqualTo(30);
    }

    @Test
    void scheduleRetryMovesFailedActionBackToScheduledWithNextRunAt() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-22T10:00:00+08:00");
        TaskAction failedAction = action(TaskStatus.FAILED, scheduledOnce(), "SMTP 连接失败");

        TaskAction retryAction = retryPolicy.scheduleRetry(failedAction, 1, now);

        assertThat(retryAction.status()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(retryAction.schedule().nextRunAt()).isEqualTo("2026-06-22T10:02+08:00");
        assertThat(retryAction.schedule().cron()).isEqualTo(failedAction.schedule().cron());
        assertThat(retryAction.executionNote()).contains("第 2 次重试");
        assertThat(retryAction.executionNote()).contains("SMTP 连接失败");
    }

    @Test
    void scheduleRetryCreatesOneTimeScheduleWhenFailedActionHasNoSchedule() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-22T10:00:00+08:00");
        TaskAction failedAction = action(TaskStatus.FAILED, null, "缺少收件人");

        TaskAction retryAction = retryPolicy.scheduleRetry(failedAction, null, now);

        assertThat(retryAction.status()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(retryAction.schedule().scheduleType()).isEqualTo("once");
        assertThat(retryAction.schedule().originalText()).isEqualTo("retry");
        assertThat(retryAction.schedule().runAt()).isEqualTo("2026-06-22T10:01+08:00");
        assertThat(retryAction.schedule().nextRunAt()).isEqualTo("2026-06-22T10:01+08:00");
        assertThat(retryAction.executionNote()).contains("第 1 次重试");
    }

    private static TaskAction action(TaskStatus status, TaskSchedule schedule, String note) {
        return new TaskAction(
                "action-test",
                "send_email",
                "email",
                "发送邮件",
                "请确认合同盖章",
                new TaskTarget("email", "李雷", "lilei@example.com"),
                schedule,
                Map.of(),
                "normal",
                "HIGH",
                0.88,
                true,
                "给李雷发邮件确认合同盖章",
                "测试解析备注",
                status,
                note
        );
    }

    private static TaskSchedule scheduledOnce() {
        return new TaskSchedule(
                "once",
                "明天下午三点",
                "2026-06-23T15:00:00+08:00",
                "0 0 15 23 6 ? 2026",
                "Asia/Shanghai",
                "2026-06-23T15:00:00+08:00",
                null,
                0
        );
    }
}

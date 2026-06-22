package com.kailei.demo.service;

import com.kailei.demo.exception.ApiErrorCode;
import com.kailei.demo.exception.BusinessException;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.model.TaskTarget;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStateMachineServiceTest {

    private final TaskStateMachineService stateMachineService = new TaskStateMachineService();

    @Test
    void waitingConfirmPlanAndActionCanBeEdited() {
        TaskAction action = action(TaskStatus.WAITING_CONFIRM, null);
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action);

        assertThat(stateMachineService.canEdit(plan, action)).isTrue();
        stateMachineService.ensureEditable(plan, action);
    }

    @Test
    void nonWaitingConfirmPlanCannotBeEdited() {
        TaskAction action = action(TaskStatus.WAITING_CONFIRM, null);
        TaskPlan plan = plan(TaskStatus.SCHEDULED, action);

        assertThat(stateMachineService.canEdit(plan, action)).isFalse();
        assertThatThrownBy(() -> stateMachineService.ensureEditable(plan, action))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ApiErrorCode.TASK_STATE_INVALID);
                    assertThat(ex.details()).containsEntry("currentStatus", TaskStatus.SCHEDULED.name());
                });
    }

    @Test
    void nonWaitingConfirmActionCannotBeEdited() {
        TaskAction action = action(TaskStatus.SCHEDULED, scheduledOnce());
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action);

        assertThat(stateMachineService.canEdit(plan, action)).isFalse();
        assertThatThrownBy(() -> stateMachineService.ensureEditable(plan, action))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ApiErrorCode.TASK_STATE_INVALID);
                    assertThat(ex.details()).containsEntry("currentStatus", TaskStatus.SCHEDULED.name());
                    assertThat(ex.details()).containsEntry("actionId", action.actionId());
                });
    }

    @Test
    void planWithExecutedActionCannotBeCancelled() {
        TaskAction executedAction = action(TaskStatus.EXECUTED, null);
        TaskPlan plan = plan(TaskStatus.CONFIRMED, executedAction);

        assertThat(stateMachineService.canCancel(plan)).isFalse();
        assertThatThrownBy(() -> stateMachineService.ensureCancelable(plan))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ApiErrorCode.TASK_STATE_INVALID);
                    assertThat(ex.details()).containsEntry("currentStatus", TaskStatus.EXECUTED.name());
                });
    }

    @Test
    void onlyFailedActionCanBeRetried() {
        TaskAction failedAction = action(TaskStatus.FAILED, null);
        TaskAction executedAction = action(TaskStatus.EXECUTED, null);

        assertThat(stateMachineService.canRetry(failedAction)).isTrue();
        stateMachineService.ensureRetryable(failedAction);

        assertThat(stateMachineService.canRetry(executedAction)).isFalse();
        assertThatThrownBy(() -> stateMachineService.ensureRetryable(executedAction))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ApiErrorCode.TASK_STATE_INVALID);
                    assertThat(ex.details()).containsEntry("currentStatus", TaskStatus.EXECUTED.name());
                    assertThat(ex.details()).containsEntry("actionId", executedAction.actionId());
                });
    }

    @Test
    void waitingConfirmScheduledActionCanEnterScheduleQueue() {
        TaskAction action = action(TaskStatus.WAITING_CONFIRM, scheduledOnce());

        assertThat(stateMachineService.canSchedule(action)).isTrue();
    }

    @Test
    void confirmedActionCannotExecuteAgain() {
        TaskAction action = action(TaskStatus.CONFIRMED, null);

        assertThat(stateMachineService.canExecute(action)).isFalse();
        assertThatThrownBy(() -> stateMachineService.ensureExecutable(action))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ApiErrorCode.TASK_STATE_INVALID);
                    assertThat(ex.details()).containsEntry("currentStatus", TaskStatus.CONFIRMED.name());
                });
    }

    private static TaskPlan plan(TaskStatus status, TaskAction... actions) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-22T10:00:00+08:00");
        return new TaskPlan(
                "plan-test",
                "trace-test",
                "session-test",
                "测试任务",
                "demo-user",
                status,
                List.of(actions),
                List.of(),
                now,
                now
        );
    }

    private static TaskAction action(TaskStatus status, TaskSchedule schedule) {
        return new TaskAction(
                "action-" + status.name().toLowerCase(),
                "reminder",
                "reminder",
                "提醒事项",
                "确认合同盖章",
                new TaskTarget("user", "李雷", null),
                schedule,
                Map.of(),
                "normal",
                "LOW",
                0.9,
                false,
                "明天下午三点提醒我给李雷确认合同盖章",
                "测试解析备注",
                status,
                "测试执行说明"
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

package com.kailei.demo.service;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStateMachineServiceTest {

    private final TaskStateMachineService service = new TaskStateMachineService();

    @Test
    void canEditWhenPlanAndActionAreWaitingConfirm() {
        TaskAction action = action(TaskStatus.WAITING_CONFIRM);
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action);

        assertTrue(service.canEdit(plan, action));
        assertDoesNotThrow(() -> service.ensureEditable(plan, action));
    }

    @Test
    void cannotEditWhenPlanIsNotWaitingConfirm() {
        TaskAction action = action(TaskStatus.WAITING_CONFIRM);
        TaskPlan plan = plan(TaskStatus.SCHEDULED, action);

        assertFalse(service.canEdit(plan, action));
        assertThrows(BusinessException.class, () -> service.ensureEditable(plan, action));
    }

    @Test
    void cannotEditWhenActionIsNotWaitingConfirm() {
        TaskAction action = action(TaskStatus.SCHEDULED);
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action);

        assertFalse(service.canEdit(plan, action));
        assertThrows(BusinessException.class, () -> service.ensureEditable(plan, action));
    }

    @Test
    void canConfirmOnlyWaitingConfirmPlan() {
        assertTrue(service.canConfirm(plan(TaskStatus.WAITING_CONFIRM, action(TaskStatus.WAITING_CONFIRM))));
        assertFalse(service.canConfirm(plan(TaskStatus.SCHEDULED, action(TaskStatus.SCHEDULED))));
    }

    @Test
    void canCancelWhenNoActionExecuted() {
        TaskPlan plan = plan(TaskStatus.SCHEDULED, action(TaskStatus.SCHEDULED));

        assertTrue(service.canCancel(plan));
        assertDoesNotThrow(() -> service.ensureCancelable(plan));
    }

    @Test
    void cannotCancelWhenAnyActionExecuted() {
        TaskPlan plan = plan(TaskStatus.EXECUTED, action(TaskStatus.EXECUTED));

        assertFalse(service.canCancel(plan));
        assertThrows(BusinessException.class, () -> service.ensureCancelable(plan));
    }

    @Test
    void canRetryOnlyFailedAction() {
        TaskAction failedAction = action(TaskStatus.FAILED);
        TaskAction scheduledAction = action(TaskStatus.SCHEDULED);

        assertTrue(service.canRetry(failedAction));
        assertDoesNotThrow(() -> service.ensureRetryable(failedAction));
        assertFalse(service.canRetry(scheduledAction));
        assertThrows(BusinessException.class, () -> service.ensureRetryable(scheduledAction));
    }

    @Test
    void canScheduleWaitingConfirmScheduledAction() {
        TaskAction action = action(TaskStatus.WAITING_CONFIRM, new TaskSchedule(
                "once",
                "明天下午三点",
                "2026-06-19T15:00:00+08:00",
                "0 0 15 19 6 ? 2026",
                "Asia/Shanghai",
                "2026-06-19T15:00:00+08:00",
                null,
                0
        ));

        assertTrue(service.canSchedule(action));
    }

    @Test
    void cannotScheduleActionWithoutSchedule() {
        TaskAction action = action(TaskStatus.WAITING_CONFIRM, null);

        assertFalse(service.canSchedule(action));
    }

    @Test
    void canExecuteWaitingScheduledOrFailedAction() {
        assertTrue(service.canExecute(action(TaskStatus.WAITING_CONFIRM)));
        assertTrue(service.canExecute(action(TaskStatus.SCHEDULED)));
        assertTrue(service.canExecute(action(TaskStatus.FAILED)));
        assertFalse(service.canExecute(action(TaskStatus.CANCELLED)));
        assertFalse(service.canExecute(action(TaskStatus.EXECUTED)));
    }

    private TaskPlan plan(TaskStatus status, TaskAction... actions) {
        OffsetDateTime now = OffsetDateTime.now();
        return new TaskPlan(
                "plan-test",
                "trace-test",
                "session-test",
                "测试输入",
                "demo-user",
                status,
                List.of(actions),
                List.of(),
                now,
                now
        );
    }

    private TaskAction action(TaskStatus status) {
        return action(status, new TaskSchedule("none", null, null, null, "Asia/Shanghai", null, null, 0));
    }

    private TaskAction action(TaskStatus status, TaskSchedule schedule) {
        return new TaskAction(
                "action-test-" + status.name().toLowerCase(),
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
                "测试状态说明"
        );
    }
}

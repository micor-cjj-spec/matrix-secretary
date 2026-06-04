package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStateMachineServiceTest {

    private final TaskStateMachineService stateMachineService = new TaskStateMachineService();

    @Test
    void ensureCanEditAllowsWaitingConfirmPlanAndAction() {
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action("act-1", TaskStatus.WAITING_CONFIRM));
        TaskAction action = plan.tasks().get(0);

        assertDoesNotThrow(() -> stateMachineService.ensureCanEdit(plan, action));
    }

    @Test
    void ensureCanEditRejectsNonWaitingConfirmPlan() {
        TaskPlan plan = plan(TaskStatus.CONFIRMED, action("act-1", TaskStatus.WAITING_CONFIRM));
        TaskAction action = plan.tasks().get(0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.ensureCanEdit(plan, action)
        );
        assertEquals("只有 WAITING_CONFIRM 状态的任务计划允许编辑: plan-1", ex.getMessage());
    }

    @Test
    void ensureCanEditRejectsNonWaitingConfirmAction() {
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action("act-1", TaskStatus.SCHEDULED));
        TaskAction action = plan.tasks().get(0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.ensureCanEdit(plan, action)
        );
        assertEquals("只有 WAITING_CONFIRM 状态的任务动作允许编辑: act-1", ex.getMessage());
    }

    @Test
    void ensureCanCancelRejectsPlanWithExecutedAction() {
        TaskPlan plan = plan(
                TaskStatus.SCHEDULED,
                action("act-1", TaskStatus.EXECUTED),
                action("act-2", TaskStatus.SCHEDULED)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.ensureCanCancel(plan)
        );
        assertEquals("任务已存在已执行动作，不能整体取消: plan-1", ex.getMessage());
    }

    @Test
    void ensureCanCancelAllowsPlanWithoutExecutedAction() {
        TaskPlan plan = plan(
                TaskStatus.SCHEDULED,
                action("act-1", TaskStatus.SCHEDULED),
                action("act-2", TaskStatus.WAITING_CONFIRM)
        );

        assertDoesNotThrow(() -> stateMachineService.ensureCanCancel(plan));
    }

    @Test
    void ensureCanRetryAllowsFailedActionOnly() {
        TaskPlan plan = plan(TaskStatus.FAILED, action("act-1", TaskStatus.FAILED));
        TaskAction action = plan.tasks().get(0);

        assertDoesNotThrow(() -> stateMachineService.ensureCanRetry(plan, action));
    }

    @Test
    void ensureCanRetryRejectsNonFailedAction() {
        TaskPlan plan = plan(TaskStatus.SCHEDULED, action("act-1", TaskStatus.SCHEDULED));
        TaskAction action = plan.tasks().get(0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.ensureCanRetry(plan, action)
        );
        assertEquals("只有 FAILED 状态的动作允许重试: act-1", ex.getMessage());
    }

    @Test
    void resolvePlanStatusReturnsCancelledWhenAllActionsCancelled() {
        assertEquals(
                TaskStatus.CANCELLED,
                stateMachineService.resolvePlanStatus(List.of(
                        action("act-1", TaskStatus.CANCELLED),
                        action("act-2", TaskStatus.CANCELLED)
                ))
        );
    }

    @Test
    void resolvePlanStatusPrefersScheduledOverFailed() {
        assertEquals(
                TaskStatus.SCHEDULED,
                stateMachineService.resolvePlanStatus(List.of(
                        action("act-1", TaskStatus.SCHEDULED),
                        action("act-2", TaskStatus.FAILED)
                ))
        );
    }

    @Test
    void resolvePlanStatusReturnsFailedWhenAnyActionFailedAndNoneScheduled() {
        assertEquals(
                TaskStatus.FAILED,
                stateMachineService.resolvePlanStatus(List.of(
                        action("act-1", TaskStatus.EXECUTED),
                        action("act-2", TaskStatus.FAILED)
                ))
        );
    }

    @Test
    void resolvePlanStatusReturnsExecutedWhenAllActionsExecuted() {
        assertEquals(
                TaskStatus.EXECUTED,
                stateMachineService.resolvePlanStatus(List.of(
                        action("act-1", TaskStatus.EXECUTED),
                        action("act-2", TaskStatus.EXECUTED)
                ))
        );
    }

    @Test
    void resolvePlanStatusFallsBackToConfirmed() {
        assertEquals(
                TaskStatus.CONFIRMED,
                stateMachineService.resolvePlanStatus(List.of(
                        action("act-1", TaskStatus.WAITING_CONFIRM),
                        action("act-2", TaskStatus.CONFIRMED)
                ))
        );
    }

    private TaskPlan plan(TaskStatus status, TaskAction... actions) {
        return new TaskPlan(
                "plan-1",
                "trace-1",
                "session-1",
                "source text",
                "demo-user",
                status,
                List.of(actions),
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private TaskAction action(String actionId, TaskStatus status) {
        return new TaskAction(
                actionId,
                "reminder",
                "reminder",
                "提醒事项",
                "该喝水了",
                null,
                null,
                Map.of(),
                "normal",
                "LOW",
                0.9,
                false,
                "提醒我喝水",
                "rule test fixture",
                status,
                "test note"
        );
    }
}

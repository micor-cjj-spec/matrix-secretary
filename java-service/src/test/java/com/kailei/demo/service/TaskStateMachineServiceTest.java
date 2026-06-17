package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStateMachineServiceTest {

    private final TaskStateMachineService stateMachine = new TaskStateMachineService();

    @Test
    void onlyWaitingConfirmPlanCanBeEdited() {
        assertThat(stateMachine.canEdit(plan(TaskStatus.WAITING_CONFIRM, action("a1", TaskStatus.WAITING_CONFIRM))))
                .isTrue();

        assertThatThrownBy(() -> stateMachine.assertPlanEditable(plan(TaskStatus.SCHEDULED, action("a1", TaskStatus.WAITING_CONFIRM))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有 WAITING_CONFIRM 状态的任务计划允许编辑");
    }

    @Test
    void onlyWaitingConfirmActionCanBeEdited() {
        assertThat(stateMachine.canEdit(action("a1", TaskStatus.WAITING_CONFIRM))).isTrue();

        assertThatThrownBy(() -> stateMachine.assertActionEditable(action("a1", TaskStatus.EXECUTED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有 WAITING_CONFIRM 状态的任务动作允许编辑");
    }

    @Test
    void executedActionBlocksWholePlanCancellation() {
        TaskPlan plan = plan(TaskStatus.CONFIRMED, action("a1", TaskStatus.EXECUTED));

        assertThat(stateMachine.canCancel(plan)).isFalse();
        assertThatThrownBy(() -> stateMachine.assertPlanCancellable(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务已存在已执行动作，不能整体取消");
    }

    @Test
    void onlyFailedActionCanBeRetried() {
        assertThat(stateMachine.canRetry(action("a1", TaskStatus.FAILED))).isTrue();

        assertThatThrownBy(() -> stateMachine.assertActionRetryable(action("a1", TaskStatus.SCHEDULED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有 FAILED 状态的动作允许重试");
    }

    @Test
    void dispatchRequiresScheduledActionWithSchedule() {
        assertThat(stateMachine.canDispatch(scheduledAction("a1", TaskStatus.SCHEDULED))).isTrue();
        assertThat(stateMachine.canDispatch(action("a2", TaskStatus.SCHEDULED))).isFalse();
        assertThat(stateMachine.canDispatch(scheduledAction("a3", TaskStatus.WAITING_CONFIRM))).isFalse();
    }

    @Test
    void resolvePlanStatusUsesBusinessPriorityOrder() {
        assertThat(stateMachine.resolvePlanStatus(List.of(
                action("a1", TaskStatus.EXECUTED),
                action("a2", TaskStatus.SCHEDULED)
        ))).isEqualTo(TaskStatus.SCHEDULED);

        assertThat(stateMachine.resolvePlanStatus(List.of(
                action("a1", TaskStatus.EXECUTED),
                action("a2", TaskStatus.FAILED)
        ))).isEqualTo(TaskStatus.FAILED);

        assertThat(stateMachine.resolvePlanStatus(List.of(
                action("a1", TaskStatus.EXECUTED),
                action("a2", TaskStatus.EXECUTED)
        ))).isEqualTo(TaskStatus.EXECUTED);

        assertThat(stateMachine.resolvePlanStatus(List.of(
                action("a1", TaskStatus.CANCELLED),
                action("a2", TaskStatus.CANCELLED)
        ))).isEqualTo(TaskStatus.CANCELLED);

        assertThat(stateMachine.resolvePlanStatus(List.of(
                action("a1", TaskStatus.CONFIRMED),
                action("a2", TaskStatus.EXECUTED)
        ))).isEqualTo(TaskStatus.CONFIRMED);
    }

    private TaskPlan plan(TaskStatus status, TaskAction... actions) {
        OffsetDateTime now = OffsetDateTime.now();
        return new TaskPlan(
                "plan-1",
                "trace-1",
                "session-1",
                "source text",
                "user-1",
                status,
                List.of(actions),
                List.of(),
                now,
                now
        );
    }

    private TaskAction action(String actionId, TaskStatus status) {
        return new TaskAction(
                actionId,
                "reminder",
                "reminder",
                "提醒",
                "喝水",
                null,
                null,
                Map.of(),
                "normal",
                "LOW",
                0.9,
                false,
                "提醒我喝水",
                null,
                status,
                "测试状态"
        );
    }

    private TaskAction scheduledAction(String actionId, TaskStatus status) {
        return action(actionId, status).withSchedule(new TaskSchedule(
                "once",
                "明天下午三点",
                OffsetDateTime.now().plusHours(1).toString(),
                null,
                "Asia/Shanghai",
                OffsetDateTime.now().plusHours(1).toString(),
                null,
                0
        ));
    }
}

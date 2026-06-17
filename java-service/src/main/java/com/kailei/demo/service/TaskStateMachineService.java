package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskStateMachineService {

    public boolean canEdit(TaskPlan plan) {
        return plan != null && plan.status() == TaskStatus.WAITING_CONFIRM;
    }

    public boolean canEdit(TaskAction action) {
        return action != null && action.status() == TaskStatus.WAITING_CONFIRM;
    }

    public void assertPlanEditable(TaskPlan plan) {
        if (!canEdit(plan)) {
            throw new IllegalArgumentException("只有 WAITING_CONFIRM 状态的任务计划允许编辑: " + planId(plan));
        }
    }

    public void assertActionEditable(TaskAction action) {
        if (!canEdit(action)) {
            throw new IllegalArgumentException("只有 WAITING_CONFIRM 状态的任务动作允许编辑: " + actionId(action));
        }
    }

    public boolean canConfirm(TaskPlan plan) {
        return plan != null && plan.status() == TaskStatus.WAITING_CONFIRM;
    }

    public boolean canCancel(TaskPlan plan) {
        return plan != null && plan.tasks().stream().noneMatch(action -> action.status() == TaskStatus.EXECUTED);
    }

    public void assertPlanCancellable(TaskPlan plan) {
        if (!canCancel(plan)) {
            throw new IllegalArgumentException("任务已存在已执行动作，不能整体取消: " + planId(plan));
        }
    }

    public boolean canRetry(TaskAction action) {
        return action != null && action.status() == TaskStatus.FAILED;
    }

    public void assertActionRetryable(TaskAction action) {
        if (!canRetry(action)) {
            throw new IllegalArgumentException("只有 FAILED 状态的动作允许重试: " + actionId(action));
        }
    }

    public boolean canDispatch(TaskAction action) {
        return action != null
                && action.status() == TaskStatus.SCHEDULED
                && action.schedule() != null;
    }

    public TaskStatus resolvePlanStatus(List<TaskAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return TaskStatus.CONFIRMED;
        }
        if (actions.stream().allMatch(action -> action.status() == TaskStatus.CANCELLED)) {
            return TaskStatus.CANCELLED;
        }
        if (actions.stream().anyMatch(action -> action.status() == TaskStatus.SCHEDULED)) {
            return TaskStatus.SCHEDULED;
        }
        if (actions.stream().anyMatch(action -> action.status() == TaskStatus.FAILED)) {
            return TaskStatus.FAILED;
        }
        if (actions.stream().allMatch(action -> action.status() == TaskStatus.EXECUTED)) {
            return TaskStatus.EXECUTED;
        }
        return TaskStatus.CONFIRMED;
    }

    private String planId(TaskPlan plan) {
        return plan == null ? "null" : plan.planId();
    }

    private String actionId(TaskAction action) {
        return action == null ? "null" : action.actionId();
    }
}

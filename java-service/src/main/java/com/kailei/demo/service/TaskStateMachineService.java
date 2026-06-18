package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskStateMachineService {

    public boolean canEditPlan(TaskPlan plan) {
        return plan != null && plan.status() == TaskStatus.WAITING_CONFIRM;
    }

    public void requireEditablePlan(TaskPlan plan) {
        if (!canEditPlan(plan)) {
            throw new IllegalArgumentException("Plan is not editable: " + safePlanId(plan));
        }
    }

    public boolean canEditAction(TaskAction action) {
        return action != null && action.status() == TaskStatus.WAITING_CONFIRM;
    }

    public void requireEditableAction(TaskAction action) {
        if (!canEditAction(action)) {
            throw new IllegalArgumentException("Action is not editable: " + safeActionId(action));
        }
    }

    public boolean canConfirmPlan(TaskPlan plan) {
        return plan != null && plan.status() == TaskStatus.WAITING_CONFIRM;
    }

    public boolean canConfirmAction(TaskAction action) {
        return action != null && action.status() == TaskStatus.WAITING_CONFIRM;
    }

    public void requireConfirmableAction(TaskAction action) {
        if (!canConfirmAction(action)) {
            throw new IllegalArgumentException("Action is not confirmable: " + safeActionId(action));
        }
    }

    public boolean canCancelPlan(TaskPlan plan) {
        if (plan == null || plan.status() == TaskStatus.CANCELLED) {
            return false;
        }
        return plan.tasks().stream().noneMatch(action -> action.status() == TaskStatus.EXECUTED
                || action.status() == TaskStatus.RUNNING);
    }

    public void requireCancellablePlan(TaskPlan plan) {
        if (plan != null && plan.status() == TaskStatus.CANCELLED) {
            return;
        }
        if (!canCancelPlan(plan)) {
            throw new IllegalArgumentException("Plan is not cancellable: " + safePlanId(plan));
        }
    }

    public boolean canRetryAction(TaskAction action) {
        return action != null && (action.status() == TaskStatus.FAILED || action.status() == TaskStatus.TIMEOUT);
    }

    public void requireRetryableAction(TaskAction action) {
        if (!canRetryAction(action)) {
            throw new IllegalArgumentException("Action is not retryable: " + safeActionId(action));
        }
    }

    public boolean canDispatchScheduledAction(TaskAction action) {
        return action != null && action.status() == TaskStatus.SCHEDULED;
    }

    public boolean canExecuteAction(TaskAction action) {
        if (action == null) {
            return false;
        }
        return action.status() == TaskStatus.WAITING_CONFIRM
                || action.status() == TaskStatus.CONFIRMED
                || action.status() == TaskStatus.SCHEDULED
                || action.status() == TaskStatus.FAILED
                || action.status() == TaskStatus.TIMEOUT
                || action.status() == TaskStatus.RETRY_WAITING;
    }

    public void requireExecutableAction(TaskAction action) {
        if (!canExecuteAction(action)) {
            throw new IllegalArgumentException("Action is not executable: " + safeActionId(action));
        }
    }

    public TaskStatus resolvePlanStatus(List<TaskAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return TaskStatus.CONFIRMED;
        }
        if (actions.stream().allMatch(action -> action.status() == TaskStatus.CANCELLED)) {
            return TaskStatus.CANCELLED;
        }
        if (actions.stream().anyMatch(action -> action.status() == TaskStatus.RUNNING)) {
            return TaskStatus.RUNNING;
        }
        if (actions.stream().anyMatch(action -> action.status() == TaskStatus.RETRY_WAITING)) {
            return TaskStatus.RETRY_WAITING;
        }
        if (actions.stream().anyMatch(action -> action.status() == TaskStatus.SCHEDULED)) {
            return TaskStatus.SCHEDULED;
        }
        if (actions.stream().anyMatch(action -> action.status() == TaskStatus.TIMEOUT)) {
            return TaskStatus.TIMEOUT;
        }
        if (actions.stream().anyMatch(action -> action.status() == TaskStatus.FAILED)) {
            return TaskStatus.FAILED;
        }
        if (actions.stream().allMatch(action -> action.status() == TaskStatus.EXECUTED)) {
            return TaskStatus.EXECUTED;
        }
        return TaskStatus.CONFIRMED;
    }

    private String safePlanId(TaskPlan plan) {
        return plan == null ? "null" : plan.planId();
    }

    private String safeActionId(TaskAction action) {
        return action == null ? "null" : action.actionId();
    }
}

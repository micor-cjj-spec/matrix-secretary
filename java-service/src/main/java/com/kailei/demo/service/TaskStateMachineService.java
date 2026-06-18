package com.kailei.demo.service;

import com.kailei.demo.exception.ApiErrorCode;
import com.kailei.demo.exception.BusinessException;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TaskStateMachineService {

    public boolean canEdit(TaskPlan plan, TaskAction action) {
        return plan != null
                && action != null
                && plan.status() == TaskStatus.WAITING_CONFIRM
                && action.status() == TaskStatus.WAITING_CONFIRM;
    }

    public void ensureEditable(TaskPlan plan, TaskAction action) {
        if (plan == null) {
            throw new BusinessException(ApiErrorCode.TASK_NOT_FOUND, "任务计划不能为空");
        }
        if (plan.status() != TaskStatus.WAITING_CONFIRM) {
            throw invalidState("只有 WAITING_CONFIRM 状态的任务计划允许编辑: " + plan.planId(), plan.status(), null);
        }
        if (action == null) {
            throw new BusinessException(ApiErrorCode.TASK_ACTION_NOT_FOUND, "任务动作不能为空");
        }
        if (action.status() != TaskStatus.WAITING_CONFIRM) {
            throw invalidState("只有 WAITING_CONFIRM 状态的任务动作允许编辑: " + action.actionId(), action.status(), action.actionId());
        }
    }

    public boolean canConfirm(TaskPlan plan) {
        return plan != null && plan.status() == TaskStatus.WAITING_CONFIRM;
    }

    public void ensureConfirmable(TaskPlan plan) {
        if (plan == null) {
            throw new BusinessException(ApiErrorCode.TASK_NOT_FOUND, "任务计划不能为空");
        }
        if (!canConfirm(plan)) {
            throw invalidState("只有 WAITING_CONFIRM 状态的任务计划允许确认: " + plan.planId(), plan.status(), null);
        }
    }

    public boolean canCancel(TaskPlan plan) {
        return plan != null
                && plan.status() != TaskStatus.CANCELLED
                && plan.tasks().stream().noneMatch(action -> action.status() == TaskStatus.EXECUTED);
    }

    public void ensureCancelable(TaskPlan plan) {
        if (plan == null) {
            throw new BusinessException(ApiErrorCode.TASK_NOT_FOUND, "任务计划不能为空");
        }
        boolean hasExecutedAction = plan.tasks().stream()
                .anyMatch(action -> action.status() == TaskStatus.EXECUTED);
        if (hasExecutedAction) {
            throw invalidState("任务已存在已执行动作，不能整体取消: " + plan.planId(), TaskStatus.EXECUTED, null);
        }
    }

    public boolean canRetry(TaskAction action) {
        return action != null && action.status() == TaskStatus.FAILED;
    }

    public void ensureRetryable(TaskAction action) {
        if (action == null) {
            throw new BusinessException(ApiErrorCode.TASK_ACTION_NOT_FOUND, "任务动作不能为空");
        }
        if (!canRetry(action)) {
            throw invalidState("只有 FAILED 状态的动作允许重试: " + action.actionId(), action.status(), action.actionId());
        }
    }

    public boolean canSchedule(TaskAction action) {
        return action != null
                && action.status() == TaskStatus.WAITING_CONFIRM
                && action.schedule() != null
                && action.schedule().isScheduled();
    }

    public boolean canExecute(TaskAction action) {
        return action != null
                && (action.status() == TaskStatus.WAITING_CONFIRM
                || action.status() == TaskStatus.SCHEDULED
                || action.status() == TaskStatus.FAILED);
    }

    public void ensureExecutable(TaskAction action) {
        if (action == null) {
            throw new BusinessException(ApiErrorCode.TASK_ACTION_NOT_FOUND, "任务动作不能为空");
        }
        if (!canExecute(action)) {
            throw invalidState("当前状态不允许执行任务动作: " + action.actionId() + ", status=" + action.status(), action.status(), action.actionId());
        }
    }

    private BusinessException invalidState(String message, TaskStatus currentStatus, String actionId) {
        Map<String, String> details = new java.util.LinkedHashMap<>();
        if (currentStatus != null) {
            details.put("currentStatus", currentStatus.name());
        }
        if (actionId != null && !actionId.isBlank()) {
            details.put("actionId", actionId);
        }
        return new BusinessException(ApiErrorCode.TASK_STATE_INVALID, message, details);
    }
}

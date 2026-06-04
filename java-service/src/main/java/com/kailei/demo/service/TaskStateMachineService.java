package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskStateMachineService {

    public void ensureCanEditPlan(TaskPlan plan) {
        if (plan.status() != TaskStatus.WAITING_CONFIRM) {
            throw new IllegalArgumentException("只有 WAITING_CONFIRM 状态的任务计划允许编辑: " + plan.planId());
        }
    }

    public void ensureCanEdit(TaskPlan plan, TaskAction action) {
        ensureCanEditPlan(plan);
        if (action.status() != TaskStatus.WAITING_CONFIRM) {
            throw new IllegalArgumentException("只有 WAITING_CONFIRM 状态的任务动作允许编辑: " + action.actionId());
        }
    }

    public void ensureCanConfirm(TaskPlan plan) {
        if (plan.status() != TaskStatus.WAITING_CONFIRM) {
            throw new IllegalArgumentException("只有 WAITING_CONFIRM 状态的任务计划允许确认: " + plan.planId());
        }
    }

    public void ensureCanCancel(TaskPlan plan) {
        boolean hasExecutedAction = plan.tasks().stream()
                .anyMatch(action -> action.status() == TaskStatus.EXECUTED);
        if (hasExecutedAction) {
            throw new IllegalArgumentException("任务已存在已执行动作，不能整体取消: " + plan.planId());
        }
    }

    public void ensureCanRetry(TaskPlan plan, TaskAction action) {
        if (action.status() != TaskStatus.FAILED) {
            throw new IllegalArgumentException("只有 FAILED 状态的动作允许重试: " + action.actionId());
        }
    }

    public TaskStatus resolvePlanStatus(List<TaskAction> actions) {
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

    public TaskAction transition(String planId,
                                 TaskAction before,
                                 TaskStatus nextStatus,
                                 String note,
                                 String operatorUserId) {
        return before.withStatus(nextStatus, note);
    }
}

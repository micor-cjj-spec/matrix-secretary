package com.kailei.demo.service;

import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.ExecutionSummary;
import com.kailei.demo.model.ReopenFinalFailureRequest;
import com.kailei.demo.model.ResolveManualReviewRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.AiSessionRepository;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TaskCommandService {

    private final TaskPlanRepository taskPlanRepository;
    private final TaskExecutionService executionService;
    private final CronScheduleService cronScheduleService;
    private final TaskExecutionLogRepository executionLogRepository;
    private final AiSessionRepository sessionRepository;
    private final TaskStateMachineService stateMachineService;
    private final TaskQueryService taskQueryService;

    public TaskCommandService(TaskPlanRepository taskPlanRepository,
                              TaskExecutionService executionService,
                              CronScheduleService cronScheduleService,
                              TaskExecutionLogRepository executionLogRepository,
                              AiSessionRepository sessionRepository,
                              TaskStateMachineService stateMachineService,
                              TaskQueryService taskQueryService) {
        this.taskPlanRepository = taskPlanRepository;
        this.executionService = executionService;
        this.cronScheduleService = cronScheduleService;
        this.executionLogRepository = executionLogRepository;
        this.sessionRepository = sessionRepository;
        this.stateMachineService = stateMachineService;
        this.taskQueryService = taskQueryService;
    }

    public TaskPlan editAction(String planId, String actionId, EditTaskActionRequest request) {
        TaskPlan plan = taskQueryService.get(planId);
        String requestedOperator = request == null ? null : request.operatorUserId();
        ensureOperatorCanAccess(plan, requestedOperator);
        String effectiveOperator = effectiveOperator(requestedOperator, plan.userId());
        stateMachineService.ensureCanEditPlan(plan);

        List<TaskAction> nextActions = new ArrayList<>();
        boolean matched = false;
        for (TaskAction action : plan.tasks()) {
            if (!action.actionId().equals(actionId)) {
                nextActions.add(action);
                continue;
            }
            matched = true;
            stateMachineService.ensureCanEdit(plan, action);
            TaskSchedule nextSchedule = request == null || request.schedule() == null
                    ? null
                    : cronScheduleService.ensureCronAndNextRun(request.schedule());
            TaskAction edited = action.withEditableFields(
                    request == null ? null : request.title(),
                    request == null ? null : request.content(),
                    request == null ? null : request.target(),
                    nextSchedule,
                    request == null ? null : request.args(),
                    request == null ? null : request.priority(),
                    request == null ? null : request.requiresConfirmation()
            ).withStatus(TaskStatus.WAITING_CONFIRM, "用户已编辑任务参数，等待确认");
            executionLogRepository.logStateChange(plan.planId(), action, edited, effectiveOperator);
            nextActions.add(edited);
        }
        if (!matched) {
            throw new IllegalArgumentException("任务动作不存在: " + actionId);
        }
        TaskPlan saved = taskPlanRepository.save(plan.withStatus(TaskStatus.WAITING_CONFIRM, nextActions));
        sessionRepository.updateAfterPlanChange(saved);
        return saved;
    }

    public ConfirmTaskResponse confirm(String planId, String operatorUserId) {
        TaskPlan plan = taskQueryService.get(planId);
        ensureOperatorCanAccess(plan, operatorUserId);
        String effectiveOperator = effectiveOperator(operatorUserId, plan.userId());
        if (plan.status() != TaskStatus.WAITING_CONFIRM) {
            return new ConfirmTaskResponse(plan.planId(), plan.status(), summarize(plan.tasks()), plan);
        }

        List<TaskAction> nextActions = plan.tasks().stream()
                .map(action -> executionService.confirmAction(plan.planId(), plan.userId(), action, effectiveOperator))
                .toList();

        TaskPlan nextPlan = plan.withStatus(stateMachineService.resolvePlanStatus(nextActions), nextActions);
        TaskPlan saved = taskPlanRepository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    public ConfirmTaskResponse cancel(String planId, String operatorUserId, String reason) {
        TaskPlan plan = taskQueryService.get(planId);
        ensureOperatorCanAccess(plan, operatorUserId);
        String effectiveOperator = effectiveOperator(operatorUserId, plan.userId());
        if (plan.status() == TaskStatus.CANCELLED) {
            return new ConfirmTaskResponse(plan.planId(), plan.status(), summarize(plan.tasks()), plan);
        }
        stateMachineService.ensureCanCancel(plan);

        String cancelReason = reason == null || reason.isBlank() ? "用户主动取消" : reason;
        List<TaskAction> nextActions = plan.tasks().stream()
                .map(action -> {
                    if (action.status() == TaskStatus.CANCELLED) {
                        return action;
                    }
                    TaskAction next = action.withStatus(TaskStatus.CANCELLED, "任务已取消: " + cancelReason);
                    executionLogRepository.logStateChange(plan.planId(), action, next, effectiveOperator);
                    return next;
                })
                .toList();
        TaskPlan nextPlan = plan.withStatus(TaskStatus.CANCELLED, nextActions);
        TaskPlan saved = taskPlanRepository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    public ConfirmTaskResponse retryAction(String planId, String actionId, String operatorUserId) {
        TaskPlan plan = taskQueryService.get(planId);
        ensureOperatorCanAccess(plan, operatorUserId);
        String effectiveOperator = effectiveOperator(operatorUserId, plan.userId());
        List<TaskAction> nextActions = new ArrayList<>();
        boolean matched = false;
        for (TaskAction action : plan.tasks()) {
            if (!action.actionId().equals(actionId)) {
                nextActions.add(action);
                continue;
            }
            matched = true;
            stateMachineService.ensureCanRetry(plan, action);
            nextActions.add(executionService.executeNow(plan.planId(), plan.userId(), action, effectiveOperator));
        }
        if (!matched) {
            throw new IllegalArgumentException("任务动作不存在: " + actionId);
        }
        TaskPlan nextPlan = plan.withStatus(stateMachineService.resolvePlanStatus(nextActions), nextActions);
        TaskPlan saved = taskPlanRepository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    public ConfirmTaskResponse resolveManualReview(String planId, String actionId, ResolveManualReviewRequest request) {
        TaskPlan plan = taskQueryService.get(planId);
        String requestedOperator = request == null ? null : request.operatorUserId();
        ensureOperatorCanAccess(plan, requestedOperator);
        String effectiveOperator = effectiveOperator(requestedOperator, plan.userId());
        List<TaskAction> nextActions = new ArrayList<>();
        boolean matched = false;
        for (TaskAction action : plan.tasks()) {
            if (!action.actionId().equals(actionId)) {
                nextActions.add(action);
                continue;
            }
            matched = true;
            stateMachineService.ensureCanResolveManualReview(plan, action);
            TaskSchedule nextSchedule = request == null || request.schedule() == null
                    ? action.schedule()
                    : cronScheduleService.ensureCronAndNextRun(request.schedule());
            TaskAction fixed = action.withEditableFields(
                    request == null ? null : request.title(),
                    request == null ? null : request.content(),
                    request == null ? null : request.target(),
                    nextSchedule,
                    request == null ? null : request.args(),
                    request == null ? null : request.priority(),
                    request == null ? null : request.requiresConfirmation()
            );
            TaskAction next = resolveFixedAction(plan, fixed, effectiveOperator, Boolean.TRUE.equals(request == null ? null : request.executeNow()), resolveNote(request));
            executionLogRepository.logStateChange(plan.planId(), action, next, effectiveOperator);
            nextActions.add(next);
        }
        if (!matched) {
            throw new IllegalArgumentException("任务动作不存在: " + actionId);
        }
        TaskPlan nextPlan = plan.withStatus(stateMachineService.resolvePlanStatus(nextActions), nextActions);
        TaskPlan saved = taskPlanRepository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    public ConfirmTaskResponse reopenFinalFailure(String planId, String actionId, ReopenFinalFailureRequest request) {
        TaskPlan plan = taskQueryService.get(planId);
        String requestedOperator = request == null ? null : request.operatorUserId();
        ensureOperatorCanAccess(plan, requestedOperator);
        String effectiveOperator = effectiveOperator(requestedOperator, plan.userId());
        List<TaskAction> nextActions = new ArrayList<>();
        boolean matched = false;
        for (TaskAction action : plan.tasks()) {
            if (!action.actionId().equals(actionId)) {
                nextActions.add(action);
                continue;
            }
            matched = true;
            stateMachineService.ensureCanReopenFinalFailure(plan, action);
            TaskSchedule nextSchedule = request == null || request.schedule() == null
                    ? action.schedule()
                    : cronScheduleService.ensureCronAndNextRun(request.schedule());
            TaskAction fixed = action.withEditableFields(
                    request == null ? null : request.title(),
                    request == null ? null : request.content(),
                    request == null ? null : request.target(),
                    nextSchedule,
                    request == null ? null : request.args(),
                    request == null ? null : request.priority(),
                    request == null ? null : request.requiresConfirmation()
            );
            TaskAction next = resolveFixedAction(plan, fixed, effectiveOperator, Boolean.TRUE.equals(request == null ? null : request.executeNow()), resolveNote(request));
            executionLogRepository.logStateChange(plan.planId(), action, next, effectiveOperator);
            nextActions.add(next);
        }
        if (!matched) {
            throw new IllegalArgumentException("任务动作不存在: " + actionId);
        }
        TaskPlan nextPlan = plan.withStatus(stateMachineService.resolvePlanStatus(nextActions), nextActions);
        TaskPlan saved = taskPlanRepository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    private TaskAction resolveFixedAction(TaskPlan plan,
                                          TaskAction fixed,
                                          String effectiveOperator,
                                          boolean executeNow,
                                          String note) {
        if (executeNow || fixed.schedule() == null || !fixed.schedule().isScheduled()) {
            return executionService.executeNow(plan.planId(), plan.userId(), fixed.withStatus(TaskStatus.CONFIRMED, note), effectiveOperator);
        }
        TaskSchedule scheduled = cronScheduleService.ensureCronAndNextRun(fixed.schedule());
        return fixed.withSchedule(scheduled).withStatus(TaskStatus.SCHEDULED, note + "; 已重新进入调度队列");
    }

    private String resolveNote(ResolveManualReviewRequest request) {
        if (request != null && request.note() != null && !request.note().isBlank()) {
            return "人工处理完成: " + request.note();
        }
        return "人工处理完成";
    }

    private String resolveNote(ReopenFinalFailureRequest request) {
        if (request != null && request.note() != null && !request.note().isBlank()) {
            return "最终失败已重新打开: " + request.note();
        }
        return "最终失败已重新打开";
    }

    private void ensureOperatorCanAccess(TaskPlan plan, String operatorUserId) {
        if (plan.userId() == null || plan.userId().isBlank()) {
            return;
        }
        if (operatorUserId == null || operatorUserId.isBlank() || !plan.userId().equals(operatorUserId)) {
            throw new IllegalArgumentException("任务计划不存在或无权操作: " + plan.planId());
        }
    }

    private String effectiveOperator(String operatorUserId, String fallbackUserId) {
        return operatorUserId == null || operatorUserId.isBlank()
                ? fallbackUserId
                : operatorUserId;
    }

    private ExecutionSummary summarize(List<TaskAction> actions) {
        int executed = (int) actions.stream().filter(action -> action.status() == TaskStatus.EXECUTED).count();
        int scheduled = (int) actions.stream().filter(action -> action.status() == TaskStatus.SCHEDULED).count();
        int failed = (int) actions.stream().filter(action -> action.status() == TaskStatus.FAILED).count();
        return new ExecutionSummary(executed, scheduled, failed);
    }
}

package com.kailei.demo.service;

import com.kailei.demo.client.PythonSemanticClient;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.ExecutionSummary;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.AiSessionRepository;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class AiTaskService {

    private static final String SYSTEM_OPERATOR = "system-scheduler";

    private final PythonSemanticClient pythonClient;
    private final TaskPlanRepository repository;
    private final TaskExecutionService executionService;
    private final SkillCatalog skillCatalog;
    private final CronScheduleService cronScheduleService;
    private final TaskStateMachineService stateMachineService;
    private final TaskExecutionLogRepository executionLogRepository;
    private final AiSessionRepository sessionRepository;

    public AiTaskService(PythonSemanticClient pythonClient,
                         TaskPlanRepository repository,
                         TaskExecutionService executionService,
                         SkillCatalog skillCatalog,
                         CronScheduleService cronScheduleService,
                         TaskStateMachineService stateMachineService,
                         TaskExecutionLogRepository executionLogRepository,
                         AiSessionRepository sessionRepository) {
        this.pythonClient = pythonClient;
        this.repository = repository;
        this.executionService = executionService;
        this.skillCatalog = skillCatalog;
        this.cronScheduleService = cronScheduleService;
        this.stateMachineService = stateMachineService;
        this.executionLogRepository = executionLogRepository;
        this.sessionRepository = sessionRepository;
    }

    public TaskPlan preview(PreviewTaskRequest request) {
        String traceId = "trace-" + UUID.randomUUID().toString().substring(0, 8);
        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
        SessionState session = sessionRepository.ensure(request.sessionId(), request.userId());

        PythonSemanticClient.PythonParseResponse parsed = pythonClient.parse(
                new PythonSemanticClient.PythonParseRequest(
                        request.text(),
                        request.effectiveTimezone(),
                        request.userId(),
                        traceId
                )
        );

        List<TaskAction> actions = IntStream.range(0, parsed.tasks().size())
                .mapToObj(index -> toTaskAction(planId, parsed.tasks().get(index), index))
                .toList();

        TaskPlan plan = new TaskPlan(
                planId,
                parsed.traceId(),
                session.sessionId(),
                request.text(),
                request.userId(),
                TaskStatus.WAITING_CONFIRM,
                actions,
                parsed.warnings() == null ? List.of() : parsed.warnings(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        TaskPlan saved = repository.save(plan);
        sessionRepository.updateAfterPreview(saved);
        return saved;
    }

    public TaskPlan editAction(String planId, String actionId, EditTaskActionRequest request) {
        TaskPlan plan = get(planId);
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
            stateMachineService.ensureEditable(plan, action);
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
        TaskPlan saved = repository.save(plan.withStatus(TaskStatus.WAITING_CONFIRM, nextActions));
        sessionRepository.updateAfterPlanChange(saved);
        return saved;
    }

    private TaskAction toTaskAction(String planId, PythonSemanticClient.PythonTaskAction item, int index) {
        SkillDefinition skill = item.skillName() != null && !item.skillName().isBlank()
                ? skillCatalog.findByName(item.skillName()).orElseGet(() -> skillCatalog.getOrUnknown(item.actionType()))
                : skillCatalog.getOrUnknown(item.actionType());
        boolean requiresConfirmation = Boolean.TRUE.equals(item.requiresConfirmation())
                || Boolean.TRUE.equals(skill.requiresConfirmation())
                || "HIGH".equalsIgnoreCase(skill.riskLevel());
        TaskSchedule schedule = cronScheduleService.ensureCronAndNextRun(item.schedule());
        return new TaskAction(
                uniqueActionId(planId, item.actionId(), index),
                item.actionType(),
                skill.name(),
                item.title(),
                item.content(),
                item.target(),
                schedule,
                item.args(),
                item.priority(),
                skill.riskLevel(),
                item.confidence(),
                requiresConfirmation,
                item.sourceSentence(),
                item.analysisNote(),
                TaskStatus.WAITING_CONFIRM,
                "等待用户确认"
        );
    }

    private String uniqueActionId(String planId, String parserActionId, int index) {
        String suffix = parserActionId == null || parserActionId.isBlank()
                ? "act-" + (index + 1)
                : parserActionId;
        return (planId + "-" + suffix).replaceAll("[^A-Za-z0-9_-]", "-");
    }

    public ConfirmTaskResponse confirm(String planId, String operatorUserId) {
        TaskPlan plan = get(planId);
        ensureOperatorCanAccess(plan, operatorUserId);
        String effectiveOperator = effectiveOperator(operatorUserId, plan.userId());
        if (!stateMachineService.canConfirm(plan)) {
            return new ConfirmTaskResponse(plan.planId(), plan.status(), summarize(plan.tasks()), plan);
        }

        List<TaskAction> nextActions = plan.tasks().stream()
                .map(action -> executionService.confirmAction(plan.planId(), plan.userId(), action, effectiveOperator))
                .toList();

        TaskPlan nextPlan = plan.withStatus(resolvePlanStatus(nextActions), nextActions);
        TaskPlan saved = repository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    public ConfirmTaskResponse cancel(String planId, String operatorUserId, String reason) {
        TaskPlan plan = get(planId);
        ensureOperatorCanAccess(plan, operatorUserId);
        String effectiveOperator = effectiveOperator(operatorUserId, plan.userId());
        if (plan.status() == TaskStatus.CANCELLED) {
            return new ConfirmTaskResponse(plan.planId(), plan.status(), summarize(plan.tasks()), plan);
        }
        stateMachineService.ensureCancelable(plan);

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
        TaskPlan saved = repository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    public ConfirmTaskResponse retryAction(String planId, String actionId, String operatorUserId) {
        TaskPlan plan = get(planId);
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
            stateMachineService.ensureRetryable(action);
            nextActions.add(executionService.executeNow(plan.planId(), plan.userId(), action, effectiveOperator));
        }
        if (!matched) {
            throw new IllegalArgumentException("任务动作不存在: " + actionId);
        }
        TaskPlan nextPlan = plan.withStatus(resolvePlanStatus(nextActions), nextActions);
        TaskPlan saved = repository.save(nextPlan);
        sessionRepository.updateAfterPlanChange(saved);
        return new ConfirmTaskResponse(saved.planId(), saved.status(), summarize(saved.tasks()), saved);
    }

    public TaskPlan get(String planId) {
        return repository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("任务计划不存在: " + planId));
    }

    public TaskPlan get(String planId, String userId) {
        TaskPlan plan = get(planId);
        ensureSameUser(plan, userId);
        return plan;
    }

    public List<TaskPlan> list(String userId) {
        if (userId == null || userId.isBlank()) {
            return repository.findAll();
        }
        return repository.findByUserId(userId);
    }

    public List<TaskPlan> list() {
        return list(null);
    }

    public SessionState getSession(String sessionId, String userId) {
        SessionState session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        if (userId != null && !userId.isBlank() && session.userId() != null && !session.userId().equals(userId)) {
            throw new IllegalArgumentException("会话不存在或无权访问: " + sessionId);
        }
        return session;
    }

    public List<TaskPlan> listBySession(String sessionId, String userId) {
        getSession(sessionId, userId);
        return repository.findByUserIdAndSessionId(userId, sessionId);
    }

    public void dispatchDueOnceTasks() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.findAll().forEach(plan -> {
            List<TaskAction> nextActions = plan.tasks().stream()
                    .map(action -> dispatchIfDue(plan, action, now))
                    .toList();
            if (!nextActions.equals(plan.tasks())) {
                TaskPlan saved = repository.save(plan.withStatus(resolvePlanStatus(nextActions), nextActions));
                sessionRepository.updateAfterPlanChange(saved);
            }
        });
    }

    private void ensureSameUser(TaskPlan plan, String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        if (plan.userId() == null || !plan.userId().equals(userId)) {
            throw new IllegalArgumentException("任务计划不存在或无权访问: " + plan.planId());
        }
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

    private TaskStatus resolvePlanStatus(List<TaskAction> actions) {
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

    private ExecutionSummary summarize(List<TaskAction> actions) {
        int executed = (int) actions.stream().filter(action -> action.status() == TaskStatus.EXECUTED).count();
        int scheduled = (int) actions.stream().filter(action -> action.status() == TaskStatus.SCHEDULED).count();
        int failed = (int) actions.stream().filter(action -> action.status() == TaskStatus.FAILED).count();
        return new ExecutionSummary(executed, scheduled, failed);
    }

    private TaskAction dispatchIfDue(TaskPlan plan, TaskAction action, OffsetDateTime now) {
        if (action.status() != TaskStatus.SCHEDULED || action.schedule() == null) {
            return action;
        }
        TaskSchedule schedule = cronScheduleService.ensureCronAndNextRun(action.schedule());
        if (schedule == null || schedule.effectiveRunAt() == null) {
            return action.withStatus(TaskStatus.FAILED, "调度任务缺少可执行时间或cron表达式");
        }
        try {
            OffsetDateTime runAt = OffsetDateTime.parse(schedule.effectiveRunAt());
            if (runAt.isAfter(now)) {
                return action.withSchedule(schedule);
            }
            TaskAction executable = action.withSchedule(schedule);
            TaskAction executed = executionService.executeNow(plan.planId(), plan.userId(), executable, SYSTEM_OPERATOR);
            if (schedule.isRecurring() && executed.status() == TaskStatus.EXECUTED) {
                TaskSchedule nextSchedule = cronScheduleService.markTriggered(schedule, now);
                String note = "周期任务已执行，下一次触发: cron=" + nextSchedule.cron()
                        + ", nextRunAt=" + nextSchedule.nextRunAt();
                return executed.withSchedule(nextSchedule).withStatus(TaskStatus.SCHEDULED, note);
            }
            return executed;
        } catch (DateTimeParseException ex) {
            return action.withStatus(TaskStatus.FAILED, "时间格式无法解析: " + schedule.effectiveRunAt());
        }
    }
}

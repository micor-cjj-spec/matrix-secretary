package com.kailei.demo.service;

import com.kailei.demo.client.PythonSemanticClient;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.ExecutionSummary;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.TaskPlanRepository;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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

    public AiTaskService(PythonSemanticClient pythonClient,
                         TaskPlanRepository repository,
                         TaskExecutionService executionService,
                         SkillCatalog skillCatalog,
                         CronScheduleService cronScheduleService) {
        this.pythonClient = pythonClient;
        this.repository = repository;
        this.executionService = executionService;
        this.skillCatalog = skillCatalog;
        this.cronScheduleService = cronScheduleService;
    }

    public TaskPlan preview(PreviewTaskRequest request) {
        String traceId = "trace-" + UUID.randomUUID().toString().substring(0, 8);
        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);

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
                request.text(),
                request.userId(),
                TaskStatus.WAITING_CONFIRM,
                actions,
                parsed.warnings() == null ? List.of() : parsed.warnings(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        return repository.save(plan);
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
        if (plan.status() != TaskStatus.WAITING_CONFIRM) {
            return new ConfirmTaskResponse(plan.planId(), plan.status(), summarize(plan.tasks()), plan);
        }

        String effectiveOperator = operatorUserId == null || operatorUserId.isBlank()
                ? plan.userId()
                : operatorUserId;
        List<TaskAction> nextActions = plan.tasks().stream()
                .map(action -> executionService.confirmAction(plan.planId(), action, effectiveOperator))
                .toList();

        TaskPlan nextPlan = plan.withStatus(TaskStatus.CONFIRMED, nextActions);
        repository.save(nextPlan);
        return new ConfirmTaskResponse(nextPlan.planId(), nextPlan.status(), summarize(nextPlan.tasks()), nextPlan);
    }

    public TaskPlan get(String planId) {
        return repository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("任务计划不存在: " + planId));
    }

    public List<TaskPlan> list() {
        return repository.findAll();
    }

    public void dispatchDueOnceTasks() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.findAll().forEach(plan -> {
            List<TaskAction> nextActions = plan.tasks().stream()
                    .map(action -> dispatchIfDue(plan.planId(), action, now))
                    .toList();
            if (!nextActions.equals(plan.tasks())) {
                repository.save(plan.withStatus(plan.status(), nextActions));
            }
        });
    }

    private ExecutionSummary summarize(List<TaskAction> actions) {
        int executed = (int) actions.stream().filter(action -> action.status() == TaskStatus.EXECUTED).count();
        int scheduled = (int) actions.stream().filter(action -> action.status() == TaskStatus.SCHEDULED).count();
        int failed = (int) actions.stream().filter(action -> action.status() == TaskStatus.FAILED).count();
        return new ExecutionSummary(executed, scheduled, failed);
    }

    private TaskAction dispatchIfDue(String planId, TaskAction action, OffsetDateTime now) {
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
            TaskAction executed = executionService.executeNow(planId, executable, SYSTEM_OPERATOR);
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

package com.kailei.demo.service;

import com.kailei.demo.client.PythonSemanticClient;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.ExecutionSummary;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.TaskPlanRepository;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class AiTaskService {

    private final PythonSemanticClient pythonClient;
    private final TaskPlanRepository repository;
    private final TaskExecutionService executionService;
    private final SkillCatalog skillCatalog;

    public AiTaskService(PythonSemanticClient pythonClient,
                         TaskPlanRepository repository,
                         TaskExecutionService executionService,
                         SkillCatalog skillCatalog) {
        this.pythonClient = pythonClient;
        this.repository = repository;
        this.executionService = executionService;
        this.skillCatalog = skillCatalog;
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

        List<TaskAction> actions = parsed.tasks().stream()
                .map(this::toTaskAction)
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

    private TaskAction toTaskAction(PythonSemanticClient.PythonTaskAction item) {
        SkillDefinition skill = item.skillName() != null && !item.skillName().isBlank()
                ? skillCatalog.findByName(item.skillName()).orElseGet(() -> skillCatalog.getOrUnknown(item.actionType()))
                : skillCatalog.getOrUnknown(item.actionType());
        boolean requiresConfirmation = Boolean.TRUE.equals(item.requiresConfirmation())
                || Boolean.TRUE.equals(skill.requiresConfirmation())
                || "HIGH".equalsIgnoreCase(skill.riskLevel());
        return new TaskAction(
                item.actionId(),
                item.actionType(),
                skill.name(),
                item.title(),
                item.content(),
                item.target(),
                item.schedule(),
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

    public ConfirmTaskResponse confirm(String planId) {
        TaskPlan plan = get(planId);
        if (plan.status() != TaskStatus.WAITING_CONFIRM) {
            return new ConfirmTaskResponse(plan.planId(), plan.status(), summarize(plan.tasks()), plan);
        }

        List<TaskAction> nextActions = plan.tasks().stream()
                .map(executionService::confirmAction)
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
                    .map(action -> dispatchIfDue(action, now))
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

    private TaskAction dispatchIfDue(TaskAction action, OffsetDateTime now) {
        if (action.status() != TaskStatus.SCHEDULED || action.schedule() == null) {
            return action;
        }
        if (!"once".equalsIgnoreCase(action.schedule().scheduleType()) || action.schedule().runAt() == null) {
            return action;
        }
        try {
            OffsetDateTime runAt = OffsetDateTime.parse(action.schedule().runAt());
            if (!runAt.isAfter(now)) {
                return executionService.executeNow(action);
            }
        } catch (DateTimeParseException ex) {
            return action.withStatus(TaskStatus.FAILED, "时间格式无法解析: " + action.schedule().runAt());
        }
        return action;
    }
}

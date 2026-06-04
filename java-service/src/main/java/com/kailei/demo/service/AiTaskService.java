package com.kailei.demo.service;

import com.kailei.demo.client.PythonSemanticClient;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.AiSessionRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class AiTaskService {

    private final PythonSemanticClient pythonClient;
    private final TaskPlanRepository repository;
    private final SkillCatalog skillCatalog;
    private final CronScheduleService cronScheduleService;
    private final AiSessionRepository sessionRepository;
    private final TaskQueryService taskQueryService;
    private final TaskDispatchService taskDispatchService;
    private final TaskCommandService taskCommandService;

    public AiTaskService(PythonSemanticClient pythonClient,
                         TaskPlanRepository repository,
                         SkillCatalog skillCatalog,
                         CronScheduleService cronScheduleService,
                         AiSessionRepository sessionRepository,
                         TaskQueryService taskQueryService,
                         TaskDispatchService taskDispatchService,
                         TaskCommandService taskCommandService) {
        this.pythonClient = pythonClient;
        this.repository = repository;
        this.skillCatalog = skillCatalog;
        this.cronScheduleService = cronScheduleService;
        this.sessionRepository = sessionRepository;
        this.taskQueryService = taskQueryService;
        this.taskDispatchService = taskDispatchService;
        this.taskCommandService = taskCommandService;
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
        return taskCommandService.editAction(planId, actionId, request);
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
        return taskCommandService.confirm(planId, operatorUserId);
    }

    public ConfirmTaskResponse cancel(String planId, String operatorUserId, String reason) {
        return taskCommandService.cancel(planId, operatorUserId, reason);
    }

    public ConfirmTaskResponse retryAction(String planId, String actionId, String operatorUserId) {
        return taskCommandService.retryAction(planId, actionId, operatorUserId);
    }

    public TaskPlan get(String planId) {
        return taskQueryService.get(planId);
    }

    public TaskPlan get(String planId, String userId) {
        return taskQueryService.get(planId, userId);
    }

    public List<TaskPlan> list(String userId) {
        return taskQueryService.list(userId);
    }

    public List<TaskPlan> list() {
        return taskQueryService.list();
    }

    public SessionState getSession(String sessionId, String userId) {
        return taskQueryService.getSession(sessionId, userId);
    }

    public List<TaskPlan> listBySession(String sessionId, String userId) {
        return taskQueryService.listBySession(sessionId, userId);
    }

    public void dispatchDueOnceTasks() {
        taskDispatchService.dispatchDueOnceTasks();
    }
}

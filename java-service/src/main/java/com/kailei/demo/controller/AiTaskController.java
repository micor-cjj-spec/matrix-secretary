package com.kailei.demo.controller;

import com.kailei.demo.entity.TaskDispatchRecordEntity;
import com.kailei.demo.entity.TaskExecutionLogEntity;
import com.kailei.demo.model.CancelTaskRequest;
import com.kailei.demo.model.ConfirmTaskRequest;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.PageResult;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.RetryTaskRequest;
import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskDetailResponse;
import com.kailei.demo.model.TaskDispatchRecordResponse;
import com.kailei.demo.model.TaskExecutionLogResponse;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.repository.TaskDispatchRecordRepository;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.service.AiTaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ai-task")
public class AiTaskController {

    private static final long DEFAULT_RECENT_LOG_SIZE = 20;
    private static final long DEFAULT_RECENT_DISPATCH_SIZE = 20;

    private final AiTaskService aiTaskService;
    private final TaskExecutionLogRepository executionLogRepository;
    private final TaskDispatchRecordRepository dispatchRecordRepository;

    public AiTaskController(AiTaskService aiTaskService,
                            TaskExecutionLogRepository executionLogRepository,
                            TaskDispatchRecordRepository dispatchRecordRepository) {
        this.aiTaskService = aiTaskService;
        this.executionLogRepository = executionLogRepository;
        this.dispatchRecordRepository = dispatchRecordRepository;
    }

    @PostMapping("/preview")
    public TaskPlan preview(@Valid @RequestBody PreviewTaskRequest request) {
        return aiTaskService.preview(request);
    }

    @PatchMapping("/{planId}/actions/{actionId}")
    public TaskPlan editAction(@PathVariable String planId,
                               @PathVariable String actionId,
                               @RequestBody(required = false) EditTaskActionRequest request) {
        return aiTaskService.editAction(planId, actionId, request);
    }

    @PostMapping("/{planId}/confirm")
    public ConfirmTaskResponse confirm(@PathVariable String planId,
                                       @RequestBody(required = false) ConfirmTaskRequest request) {
        String operatorUserId = request == null ? null : request.operatorUserId();
        return aiTaskService.confirm(planId, operatorUserId);
    }

    @PostMapping("/{planId}/cancel")
    public ConfirmTaskResponse cancel(@PathVariable String planId,
                                      @RequestBody(required = false) CancelTaskRequest request) {
        String operatorUserId = request == null ? null : request.operatorUserId();
        String reason = request == null ? null : request.reason();
        return aiTaskService.cancel(planId, operatorUserId, reason);
    }

    @PostMapping("/{planId}/actions/{actionId}/retry")
    public ConfirmTaskResponse retryAction(@PathVariable String planId,
                                           @PathVariable String actionId,
                                           @RequestBody(required = false) RetryTaskRequest request) {
        String operatorUserId = request == null ? null : request.operatorUserId();
        return aiTaskService.retryAction(planId, actionId, operatorUserId);
    }

    @GetMapping("/{planId}")
    public TaskPlan get(@PathVariable String planId,
                        @RequestParam(required = false) String userId) {
        return aiTaskService.get(planId, userId);
    }

    @GetMapping("/{planId}/detail")
    public TaskDetailResponse detail(@PathVariable String planId,
                                     @RequestParam(required = false) String userId,
                                     @RequestParam(required = false) Long recentLogSize,
                                     @RequestParam(required = false) Long recentDispatchSize) {
        TaskPlan plan = aiTaskService.get(planId, userId);
        PageResult<TaskDispatchRecordEntity> recentDispatchPage = dispatchRecordRepository.findByPlanId(
                planId,
                null,
                null,
                null,
                null,
                1L,
                recentDispatchSize == null ? DEFAULT_RECENT_DISPATCH_SIZE : recentDispatchSize
        );
        return new TaskDetailResponse(
                plan,
                executionLogRepository.findRecentByPlanId(
                                planId,
                                recentLogSize == null ? DEFAULT_RECENT_LOG_SIZE : recentLogSize
                        ).stream()
                        .map(TaskExecutionLogResponse::from)
                        .toList(),
                recentDispatchPage.records().stream().map(TaskDispatchRecordResponse::from).toList(),
                dispatchRecordRepository.summarizeByPlanId(planId)
        );
    }

    @GetMapping("/{planId}/logs")
    public List<TaskExecutionLogResponse> logs(@PathVariable String planId,
                                               @RequestParam(required = false) String userId) {
        aiTaskService.get(planId, userId);
        return toExecutionLogResponses(executionLogRepository.findByPlanId(planId));
    }

    @GetMapping("/{planId}/actions/{actionId}/logs")
    public List<TaskExecutionLogResponse> actionLogs(@PathVariable String planId,
                                                     @PathVariable String actionId,
                                                     @RequestParam(required = false) String userId) {
        aiTaskService.get(planId, userId);
        return toExecutionLogResponses(executionLogRepository.findByPlanIdAndActionId(planId, actionId));
    }

    @GetMapping("/{planId}/dispatch-records")
    public PageResult<TaskDispatchRecordResponse> dispatchRecords(@PathVariable String planId,
                                                                  @RequestParam(required = false) String userId,
                                                                  @RequestParam(required = false) String status,
                                                                  @RequestParam(required = false)
                                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                  OffsetDateTime startTime,
                                                                  @RequestParam(required = false)
                                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                  OffsetDateTime endTime,
                                                                  @RequestParam(required = false) String dispatchOwner,
                                                                  @RequestParam(required = false) Long page,
                                                                  @RequestParam(required = false) Long size) {
        aiTaskService.get(planId, userId);
        return toDispatchRecordResponsePage(dispatchRecordRepository.findByPlanId(
                planId,
                status,
                startTime,
                endTime,
                dispatchOwner,
                page,
                size
        ));
    }

    @GetMapping("/{planId}/actions/{actionId}/dispatch-records")
    public PageResult<TaskDispatchRecordResponse> actionDispatchRecords(@PathVariable String planId,
                                                                        @PathVariable String actionId,
                                                                        @RequestParam(required = false) String userId,
                                                                        @RequestParam(required = false) String status,
                                                                        @RequestParam(required = false)
                                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                        OffsetDateTime startTime,
                                                                        @RequestParam(required = false)
                                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                        OffsetDateTime endTime,
                                                                        @RequestParam(required = false) String dispatchOwner,
                                                                        @RequestParam(required = false) Long page,
                                                                        @RequestParam(required = false) Long size) {
        aiTaskService.get(planId, userId);
        return toDispatchRecordResponsePage(dispatchRecordRepository.findByPlanIdAndActionId(
                planId,
                actionId,
                status,
                startTime,
                endTime,
                dispatchOwner,
                page,
                size
        ));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionState getSession(@PathVariable String sessionId,
                                   @RequestParam(required = false) String userId) {
        return aiTaskService.getSession(sessionId, userId);
    }

    @GetMapping("/sessions/{sessionId}/plans")
    public PageResult<TaskPlan> listSessionPlans(@PathVariable String sessionId,
                                                 @RequestParam(required = false) String userId,
                                                 @RequestParam(required = false) Long page,
                                                 @RequestParam(required = false) Long size) {
        return aiTaskService.listBySession(sessionId, userId, page, size);
    }

    @GetMapping
    public PageResult<TaskPlan> list(@RequestParam(required = false) String userId,
                                     @RequestParam(required = false) Long page,
                                     @RequestParam(required = false) Long size) {
        return aiTaskService.list(userId, page, size);
    }

    private List<TaskExecutionLogResponse> toExecutionLogResponses(List<TaskExecutionLogEntity> logs) {
        return logs.stream().map(TaskExecutionLogResponse::from).toList();
    }

    private PageResult<TaskDispatchRecordResponse> toDispatchRecordResponsePage(PageResult<TaskDispatchRecordEntity> page) {
        return new PageResult<>(
                page.records().stream().map(TaskDispatchRecordResponse::from).toList(),
                page.total(),
                page.page(),
                page.size(),
                page.pages()
        );
    }
}

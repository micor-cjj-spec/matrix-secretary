package com.kailei.demo.controller;

import com.kailei.demo.entity.TaskExecutionLogEntity;
import com.kailei.demo.channel.entity.ChannelEventLogEntity;
import com.kailei.demo.channel.repository.ChannelEventLogRepository;
import com.kailei.demo.model.CancelTaskRequest;
import com.kailei.demo.model.ConfirmTaskRequest;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.ReopenFinalFailureRequest;
import com.kailei.demo.model.ResolveManualReviewRequest;
import com.kailei.demo.model.RetryTaskRequest;
import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.service.TaskCommandService;
import com.kailei.demo.service.TaskPreviewService;
import com.kailei.demo.service.TaskQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/task-center")
public class TaskCenterController {

    private final TaskPreviewService taskPreviewService;
    private final TaskCommandService taskCommandService;
    private final TaskQueryService taskQueryService;
    private final TaskExecutionLogRepository executionLogRepository;
    private final ChannelEventLogRepository channelEventLogRepository;

    public TaskCenterController(TaskPreviewService taskPreviewService,
                                TaskCommandService taskCommandService,
                                TaskQueryService taskQueryService,
                                TaskExecutionLogRepository executionLogRepository,
                                ChannelEventLogRepository channelEventLogRepository) {
        this.taskPreviewService = taskPreviewService;
        this.taskCommandService = taskCommandService;
        this.taskQueryService = taskQueryService;
        this.executionLogRepository = executionLogRepository;
        this.channelEventLogRepository = channelEventLogRepository;
    }

    @PostMapping("/plans/preview")
    public TaskPlan preview(@Valid @RequestBody PreviewTaskRequest request) {
        return taskPreviewService.preview(request);
    }

    @GetMapping("/plans/{planId}")
    public TaskPlan get(@PathVariable String planId,
                        @RequestParam(required = false) String userId) {
        return taskQueryService.get(planId, userId);
    }

    @GetMapping("/plans")
    public List<TaskPlan> list(@RequestParam(required = false) String userId) {
        return taskQueryService.list(userId);
    }

    @PatchMapping("/plans/{planId}/actions/{actionId}")
    public TaskPlan editAction(@PathVariable String planId,
                               @PathVariable String actionId,
                               @RequestBody(required = false) EditTaskActionRequest request) {
        return taskCommandService.editAction(planId, actionId, request);
    }

    @PostMapping("/plans/{planId}/confirm")
    public ConfirmTaskResponse confirm(@PathVariable String planId,
                                       @RequestBody(required = false) ConfirmTaskRequest request) {
        String operatorUserId = request == null ? null : request.operatorUserId();
        return taskCommandService.confirm(planId, operatorUserId);
    }

    @PostMapping("/plans/{planId}/cancel")
    public ConfirmTaskResponse cancel(@PathVariable String planId,
                                      @RequestBody(required = false) CancelTaskRequest request) {
        String operatorUserId = request == null ? null : request.operatorUserId();
        String reason = request == null ? null : request.reason();
        return taskCommandService.cancel(planId, operatorUserId, reason);
    }

    @PostMapping("/plans/{planId}/actions/{actionId}/retry")
    public ConfirmTaskResponse retryAction(@PathVariable String planId,
                                           @PathVariable String actionId,
                                           @RequestBody(required = false) RetryTaskRequest request) {
        String operatorUserId = request == null ? null : request.operatorUserId();
        return taskCommandService.retryAction(planId, actionId, operatorUserId);
    }

    @PostMapping("/plans/{planId}/actions/{actionId}/manual-resolve")
    public ConfirmTaskResponse resolveManualReview(@PathVariable String planId,
                                                   @PathVariable String actionId,
                                                   @RequestBody(required = false) ResolveManualReviewRequest request) {
        return taskCommandService.resolveManualReview(planId, actionId, request);
    }

    @PostMapping("/plans/{planId}/actions/{actionId}/reopen-final-failure")
    public ConfirmTaskResponse reopenFinalFailure(@PathVariable String planId,
                                                  @PathVariable String actionId,
                                                  @RequestBody(required = false) ReopenFinalFailureRequest request) {
        return taskCommandService.reopenFinalFailure(planId, actionId, request);
    }

    @GetMapping("/plans/{planId}/logs")
    public List<TaskExecutionLogEntity> logs(@PathVariable String planId,
                                             @RequestParam(required = false) String userId) {
        taskQueryService.get(planId, userId);
        return executionLogRepository.findByPlanId(planId);
    }

    @GetMapping("/plans/{planId}/actions/{actionId}/logs")
    public List<TaskExecutionLogEntity> actionLogs(@PathVariable String planId,
                                                   @PathVariable String actionId,
                                                   @RequestParam(required = false) String userId) {
        taskQueryService.get(planId, userId);
        return executionLogRepository.findByPlanIdAndActionId(planId, actionId);
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionState getSession(@PathVariable String sessionId,
                                   @RequestParam(required = false) String userId) {
        return taskQueryService.getSession(sessionId, userId);
    }

    @GetMapping("/sessions/{sessionId}/plans")
    public List<TaskPlan> listSessionPlans(@PathVariable String sessionId,
                                           @RequestParam(required = false) String userId) {
        return taskQueryService.listBySession(sessionId, userId);
    }

    @GetMapping("/channel-events")
    public List<ChannelEventLogEntity> channelEvents(@RequestParam(defaultValue = "20") int limit) {
        return channelEventLogRepository.findRecent(limit);
    }
}

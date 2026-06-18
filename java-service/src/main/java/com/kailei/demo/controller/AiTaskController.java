package com.kailei.demo.controller;

import com.kailei.demo.entity.TaskExecutionLogEntity;
import com.kailei.demo.model.CancelTaskRequest;
import com.kailei.demo.model.ConfirmTaskRequest;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.RetryTaskRequest;
import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskPlanPageResponse;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.service.AiTaskService;
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
@RequestMapping("/api/ai-task")
public class AiTaskController {

    private final AiTaskService aiTaskService;
    private final TaskExecutionLogRepository executionLogRepository;

    public AiTaskController(AiTaskService aiTaskService,
                            TaskExecutionLogRepository executionLogRepository) {
        this.aiTaskService = aiTaskService;
        this.executionLogRepository = executionLogRepository;
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

    @GetMapping("/page")
    public TaskPlanPageResponse page(@RequestParam(required = false) String userId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String sessionId,
                                     @RequestParam(required = false) Integer pageNo,
                                     @RequestParam(required = false) Integer pageSize) {
        return aiTaskService.page(userId, status, sessionId, pageNo, pageSize);
    }

    @GetMapping("/{planId}")
    public TaskPlan get(@PathVariable String planId,
                        @RequestParam(required = false) String userId) {
        return aiTaskService.get(planId, userId);
    }

    @GetMapping("/{planId}/logs")
    public List<TaskExecutionLogEntity> logs(@PathVariable String planId,
                                             @RequestParam(required = false) String userId) {
        aiTaskService.get(planId, userId);
        return executionLogRepository.findByPlanId(planId);
    }

    @GetMapping("/{planId}/actions/{actionId}/logs")
    public List<TaskExecutionLogEntity> actionLogs(@PathVariable String planId,
                                                   @PathVariable String actionId,
                                                   @RequestParam(required = false) String userId) {
        aiTaskService.get(planId, userId);
        return executionLogRepository.findByPlanIdAndActionId(planId, actionId);
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionState getSession(@PathVariable String sessionId,
                                   @RequestParam(required = false) String userId) {
        return aiTaskService.getSession(sessionId, userId);
    }

    @GetMapping("/sessions/{sessionId}/plans")
    public List<TaskPlan> listSessionPlans(@PathVariable String sessionId,
                                           @RequestParam(required = false) String userId) {
        return aiTaskService.listBySession(sessionId, userId);
    }

    @GetMapping
    public List<TaskPlan> list(@RequestParam(required = false) String userId) {
        return aiTaskService.list(userId);
    }
}

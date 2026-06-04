package com.kailei.demo.service;

import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.ReopenFinalFailureRequest;
import com.kailei.demo.model.ResolveManualReviewRequest;
import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskPlan;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiTaskService {

    private final TaskPreviewService taskPreviewService;
    private final TaskQueryService taskQueryService;
    private final TaskDispatchService taskDispatchService;
    private final TaskCommandService taskCommandService;

    public AiTaskService(TaskPreviewService taskPreviewService,
                         TaskQueryService taskQueryService,
                         TaskDispatchService taskDispatchService,
                         TaskCommandService taskCommandService) {
        this.taskPreviewService = taskPreviewService;
        this.taskQueryService = taskQueryService;
        this.taskDispatchService = taskDispatchService;
        this.taskCommandService = taskCommandService;
    }

    public TaskPlan preview(PreviewTaskRequest request) {
        return taskPreviewService.preview(request);
    }

    public TaskPlan editAction(String planId, String actionId, EditTaskActionRequest request) {
        return taskCommandService.editAction(planId, actionId, request);
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

    public ConfirmTaskResponse resolveManualReview(String planId, String actionId, ResolveManualReviewRequest request) {
        return taskCommandService.resolveManualReview(planId, actionId, request);
    }

    public ConfirmTaskResponse reopenFinalFailure(String planId, String actionId, ReopenFinalFailureRequest request) {
        return taskCommandService.reopenFinalFailure(planId, actionId, request);
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

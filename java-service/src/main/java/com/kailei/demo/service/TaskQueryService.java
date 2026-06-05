package com.kailei.demo.service;

import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.repository.AiSessionRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskQueryService {

    private final TaskPlanRepository taskPlanRepository;
    private final AiSessionRepository sessionRepository;

    public TaskQueryService(TaskPlanRepository taskPlanRepository, AiSessionRepository sessionRepository) {
        this.taskPlanRepository = taskPlanRepository;
        this.sessionRepository = sessionRepository;
    }

    public TaskPlan get(String planId) {
        return taskPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("任务计划不存在: " + planId));
    }

    public TaskPlan get(String planId, String userId) {
        requireUserId(userId);
        TaskPlan plan = get(planId);
        ensureSameUser(plan, userId);
        return plan;
    }

    public List<TaskPlan> list(String userId) {
        requireUserId(userId);
        return taskPlanRepository.findByUserId(userId);
    }

    public List<TaskPlan> list() {
        throw new IllegalArgumentException("缺少 userId，不允许查询全部任务");
    }

    public SessionState getSession(String sessionId, String userId) {
        requireUserId(userId);
        SessionState session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        if (session.userId() == null || !session.userId().equals(userId)) {
            throw new IllegalArgumentException("会话不存在或无权访问: " + sessionId);
        }
        return session;
    }

    public List<TaskPlan> listBySession(String sessionId, String userId) {
        getSession(sessionId, userId);
        return taskPlanRepository.findByUserIdAndSessionId(userId, sessionId);
    }

    private void ensureSameUser(TaskPlan plan, String userId) {
        if (plan.userId() == null || !plan.userId().equals(userId)) {
            throw new IllegalArgumentException("任务计划不存在或无权访问: " + plan.planId());
        }
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("缺少 userId");
        }
    }
}

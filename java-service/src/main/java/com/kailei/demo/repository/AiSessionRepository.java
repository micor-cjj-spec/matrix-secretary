package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kailei.demo.entity.AiSessionEntity;
import com.kailei.demo.mapper.AiSessionMapper;
import com.kailei.demo.model.SessionState;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiSessionRepository {

    private static final String STATUS_IDLE = "IDLE";
    private static final String STATUS_WAITING_CONFIRM = "WAITING_CONFIRM";
    private static final String STATUS_PROCESSING = "PROCESSING";

    private final AiSessionMapper mapper;

    public AiSessionRepository(AiSessionMapper mapper) {
        this.mapper = mapper;
    }

    public SessionState ensure(String sessionId, String userId) {
        String effectiveSessionId = sessionId == null || sessionId.isBlank()
                ? "session-" + UUID.randomUUID().toString().substring(0, 12)
                : sessionId;
        AiSessionEntity existing = mapper.selectById(effectiveSessionId);
        if (existing != null) {
            ensureSameUser(existing, userId);
            return toDomain(existing);
        }
        OffsetDateTime now = OffsetDateTime.now();
        AiSessionEntity entity = new AiSessionEntity();
        entity.setSessionId(effectiveSessionId);
        entity.setUserId(userId);
        entity.setStatus(STATUS_IDLE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return toDomain(entity);
    }

    public Optional<SessionState> findById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(sessionId)).map(this::toDomain);
    }

    public SessionState updateAfterPreview(TaskPlan plan) {
        AiSessionEntity entity = mapper.selectById(plan.sessionId());
        if (entity == null) {
            ensure(plan.sessionId(), plan.userId());
            entity = mapper.selectById(plan.sessionId());
        }
        entity.setUserId(plan.userId());
        entity.setLastPlanId(plan.planId());
        entity.setPendingActionId(resolvePendingActionId(plan));
        entity.setStatus(resolveSessionStatus(plan));
        entity.setLastUserInput(plan.sourceText());
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(entity);
        return toDomain(entity);
    }

    public SessionState updateAfterPlanChange(TaskPlan plan) {
        AiSessionEntity entity = mapper.selectById(plan.sessionId());
        if (entity == null) {
            return updateAfterPreview(plan);
        }
        entity.setLastPlanId(plan.planId());
        entity.setPendingActionId(resolvePendingActionId(plan));
        entity.setStatus(resolveSessionStatus(plan));
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(entity);
        return toDomain(entity);
    }

    public Optional<SessionState> latestByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return mapper.selectList(new LambdaQueryWrapper<AiSessionEntity>()
                        .eq(AiSessionEntity::getUserId, userId)
                        .orderByDesc(AiSessionEntity::getUpdatedAt)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    private String resolvePendingActionId(TaskPlan plan) {
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
            return null;
        }
        return plan.tasks().stream()
                .filter(action -> action.status() == TaskStatus.WAITING_CONFIRM)
                .min(Comparator.comparing(TaskAction::actionId))
                .map(TaskAction::actionId)
                .orElse(null);
    }

    private String resolveSessionStatus(TaskPlan plan) {
        if (plan == null || plan.status() == null) {
            return STATUS_IDLE;
        }
        if (plan.status() == TaskStatus.WAITING_CONFIRM) {
            return STATUS_WAITING_CONFIRM;
        }
        if (plan.status() == TaskStatus.SCHEDULED || plan.status() == TaskStatus.CONFIRMED) {
            return STATUS_PROCESSING;
        }
        return STATUS_IDLE;
    }

    private void ensureSameUser(AiSessionEntity entity, String userId) {
        if (entity.getUserId() == null || entity.getUserId().isBlank() || userId == null || userId.isBlank()) {
            return;
        }
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("会话不存在或无权访问: " + entity.getSessionId());
        }
    }

    private SessionState toDomain(AiSessionEntity entity) {
        return new SessionState(
                entity.getSessionId(),
                entity.getUserId(),
                entity.getLastPlanId(),
                entity.getPendingActionId(),
                entity.getStatus(),
                entity.getLastUserInput(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

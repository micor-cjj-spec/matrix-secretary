package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.entity.TaskActionEntity;
import com.kailei.demo.entity.TaskPlanEntity;
import com.kailei.demo.mapper.TaskActionMapper;
import com.kailei.demo.mapper.TaskPlanMapper;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.model.TaskTarget;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class TaskPlanRepository {

    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final TaskPlanMapper taskPlanMapper;
    private final TaskActionMapper taskActionMapper;
    private final ObjectMapper objectMapper;

    public TaskPlanRepository(TaskPlanMapper taskPlanMapper,
                              TaskActionMapper taskActionMapper,
                              ObjectMapper objectMapper) {
        this.taskPlanMapper = taskPlanMapper;
        this.taskActionMapper = taskActionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskPlan save(TaskPlan plan) {
        TaskPlanEntity planEntity = toPlanEntity(plan);
        if (taskPlanMapper.updateById(planEntity) == 0) {
            taskPlanMapper.insert(planEntity);
        }
        upsertActions(plan);
        return plan;
    }

    public Optional<TaskPlan> findById(String planId) {
        TaskPlanEntity planEntity = taskPlanMapper.selectById(planId);
        if (planEntity == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(planEntity, findActions(planId)));
    }

    public List<TaskPlan> findAll() {
        return taskPlanMapper.selectList(new LambdaQueryWrapper<TaskPlanEntity>()
                        .orderByDesc(TaskPlanEntity::getCreatedAt))
                .stream()
                .map(planEntity -> toDomain(planEntity, findActions(planEntity.getPlanId())))
                .toList();
    }

    public List<TaskPlan> findByUserId(String userId) {
        return taskPlanMapper.selectList(new LambdaQueryWrapper<TaskPlanEntity>()
                        .eq(TaskPlanEntity::getUserId, userId)
                        .orderByDesc(TaskPlanEntity::getCreatedAt))
                .stream()
                .map(planEntity -> toDomain(planEntity, findActions(planEntity.getPlanId())))
                .toList();
    }

    public List<TaskPlan> findByUserIdAndSessionId(String userId, String sessionId) {
        LambdaQueryWrapper<TaskPlanEntity> wrapper = new LambdaQueryWrapper<TaskPlanEntity>()
                .eq(TaskPlanEntity::getSessionId, sessionId)
                .orderByDesc(TaskPlanEntity::getCreatedAt);
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(TaskPlanEntity::getUserId, userId);
        }
        return taskPlanMapper.selectList(wrapper)
                .stream()
                .map(planEntity -> toDomain(planEntity, findActions(planEntity.getPlanId())))
                .toList();
    }

    public List<TaskActionEntity> findDueScheduledActions(OffsetDateTime now,
                                                          OffsetDateTime lockExpiredBefore,
                                                          int limit) {
        return taskActionMapper.selectList(new LambdaQueryWrapper<TaskActionEntity>()
                .eq(TaskActionEntity::getStatus, TaskStatus.SCHEDULED.name())
                .le(TaskActionEntity::getNextFireTime, now)
                .and(wrapper -> wrapper
                        .isNull(TaskActionEntity::getLockedBy)
                        .or()
                        .isNull(TaskActionEntity::getLockedAt)
                        .or()
                        .lt(TaskActionEntity::getLockedAt, lockExpiredBefore))
                .orderByAsc(TaskActionEntity::getNextFireTime)
                .last("LIMIT " + sanitizeLimit(limit)));
    }

    public boolean tryLockScheduledAction(String actionId,
                                          OffsetDateTime now,
                                          OffsetDateTime lockExpiredBefore,
                                          String lockOwner) {
        int updated = taskActionMapper.update(null, new LambdaUpdateWrapper<TaskActionEntity>()
                .set(TaskActionEntity::getLockedBy, lockOwner)
                .set(TaskActionEntity::getLockedAt, now)
                .eq(TaskActionEntity::getActionId, actionId)
                .eq(TaskActionEntity::getStatus, TaskStatus.SCHEDULED.name())
                .le(TaskActionEntity::getNextFireTime, now)
                .and(wrapper -> wrapper
                        .isNull(TaskActionEntity::getLockedBy)
                        .or()
                        .isNull(TaskActionEntity::getLockedAt)
                        .or()
                        .lt(TaskActionEntity::getLockedAt, lockExpiredBefore)));
        return updated > 0;
    }

    public void releaseActionLock(String actionId, String lockOwner) {
        taskActionMapper.update(null, new LambdaUpdateWrapper<TaskActionEntity>()
                .set(TaskActionEntity::getLockedBy, null)
                .set(TaskActionEntity::getLockedAt, null)
                .eq(TaskActionEntity::getActionId, actionId)
                .eq(TaskActionEntity::getLockedBy, lockOwner));
    }

    private void upsertActions(TaskPlan plan) {
        List<TaskActionEntity> existingActions = findActions(plan.planId());
        Map<String, TaskActionEntity> existingById = new HashMap<>();
        for (TaskActionEntity existing : existingActions) {
            existingById.put(existing.getActionId(), existing);
        }

        Set<String> nextActionIds = new HashSet<>();
        for (int i = 0; i < plan.tasks().size(); i++) {
            TaskAction action = plan.tasks().get(i);
            nextActionIds.add(action.actionId());
            TaskActionEntity existing = existingById.get(action.actionId());
            TaskActionEntity next = toActionEntity(plan.planId(), action, i, existing);
            if (existing == null) {
                taskActionMapper.insert(next);
            } else {
                updateActionEntity(next);
            }
        }

        existingActions.stream()
                .map(TaskActionEntity::getActionId)
                .filter(existingActionId -> !nextActionIds.contains(existingActionId))
                .forEach(this::deleteActionById);
    }

    private void updateActionEntity(TaskActionEntity entity) {
        taskActionMapper.update(null, new LambdaUpdateWrapper<TaskActionEntity>()
                .set(TaskActionEntity::getPlanId, entity.getPlanId())
                .set(TaskActionEntity::getActionType, entity.getActionType())
                .set(TaskActionEntity::getSkillName, entity.getSkillName())
                .set(TaskActionEntity::getTitle, entity.getTitle())
                .set(TaskActionEntity::getContent, entity.getContent())
                .set(TaskActionEntity::getTargetJson, entity.getTargetJson())
                .set(TaskActionEntity::getScheduleJson, entity.getScheduleJson())
                .set(TaskActionEntity::getArgsJson, entity.getArgsJson())
                .set(TaskActionEntity::getPriority, entity.getPriority())
                .set(TaskActionEntity::getRiskLevel, entity.getRiskLevel())
                .set(TaskActionEntity::getConfidence, entity.getConfidence())
                .set(TaskActionEntity::getRequiresConfirmation, entity.getRequiresConfirmation())
                .set(TaskActionEntity::getSourceSentence, entity.getSourceSentence())
                .set(TaskActionEntity::getAnalysisNote, entity.getAnalysisNote())
                .set(TaskActionEntity::getStatus, entity.getStatus())
                .set(TaskActionEntity::getExecutionNote, entity.getExecutionNote())
                .set(TaskActionEntity::getSortOrder, entity.getSortOrder())
                .set(TaskActionEntity::getNextFireTime, entity.getNextFireTime())
                .set(TaskActionEntity::getLockedBy, entity.getLockedBy())
                .set(TaskActionEntity::getLockedAt, entity.getLockedAt())
                .set(TaskActionEntity::getAttemptCount, entity.getAttemptCount())
                .set(TaskActionEntity::getMaxRetryCount, entity.getMaxRetryCount())
                .set(TaskActionEntity::getIdempotencyKey, entity.getIdempotencyKey())
                .set(TaskActionEntity::getLastError, entity.getLastError())
                .eq(TaskActionEntity::getActionId, entity.getActionId()));
    }

    private void deleteActionById(String actionId) {
        taskActionMapper.delete(new LambdaQueryWrapper<TaskActionEntity>()
                .eq(TaskActionEntity::getActionId, actionId));
    }

    private List<TaskActionEntity> findActions(String planId) {
        return taskActionMapper.selectList(new LambdaQueryWrapper<TaskActionEntity>()
                .eq(TaskActionEntity::getPlanId, planId)
                .orderByAsc(TaskActionEntity::getSortOrder));
    }

    private TaskPlanEntity toPlanEntity(TaskPlan plan) {
        TaskPlanEntity entity = new TaskPlanEntity();
        entity.setPlanId(plan.planId());
        entity.setTraceId(plan.traceId());
        entity.setSessionId(plan.sessionId());
        entity.setSourceText(plan.sourceText());
        entity.setUserId(plan.userId());
        entity.setStatus(plan.status().name());
        entity.setWarningsJson(writeJson(plan.warnings()));
        entity.setCreatedAt(plan.createdAt());
        entity.setUpdatedAt(plan.updatedAt());
        return entity;
    }

    private TaskActionEntity toActionEntity(String planId, TaskAction action, int sortOrder, TaskActionEntity existing) {
        TaskActionEntity entity = new TaskActionEntity();
        entity.setActionId(action.actionId());
        entity.setPlanId(planId);
        entity.setActionType(action.actionType());
        entity.setSkillName(action.skillName());
        entity.setTitle(limit(action.title(), 255));
        entity.setContent(action.content());
        entity.setTargetJson(writeJson(action.target()));
        entity.setScheduleJson(writeJson(action.schedule()));
        entity.setArgsJson(writeJson(action.args()));
        entity.setPriority(limit(action.priority(), 32));
        entity.setRiskLevel(limit(action.riskLevel(), 32));
        entity.setConfidence(action.confidence());
        entity.setRequiresConfirmation(action.requiresConfirmation());
        entity.setSourceSentence(action.sourceSentence());
        entity.setAnalysisNote(limit(action.analysisNote(), 512));
        entity.setStatus(action.status().name());
        entity.setExecutionNote(limit(action.executionNote(), 512));
        entity.setSortOrder(sortOrder);
        entity.setNextFireTime(resolveNextFireTime(action.status(), action.schedule()));
        entity.setAttemptCount(resolveAttemptCount(existing, action));
        entity.setMaxRetryCount(existing == null || existing.getMaxRetryCount() == null ? DEFAULT_MAX_RETRY_COUNT : existing.getMaxRetryCount());
        entity.setIdempotencyKey(existing == null || existing.getIdempotencyKey() == null ? action.actionId() : existing.getIdempotencyKey());
        entity.setLastError(resolveLastError(action));
        copyLockIfStillValid(existing, entity);
        return entity;
    }

    private int resolveAttemptCount(TaskActionEntity existing, TaskAction action) {
        int current = existing == null || existing.getAttemptCount() == null ? 0 : existing.getAttemptCount();
        if (existing == null) {
            return current;
        }
        String previousStatus = existing.getStatus();
        String nextStatus = action.status().name();
        boolean terminalAttemptResult = action.status() == TaskStatus.EXECUTED
                || action.status() == TaskStatus.FAILED
                || action.status() == TaskStatus.TIMEOUT;
        boolean retryWaitingResult = action.status() == TaskStatus.RETRY_WAITING;
        boolean statusChanged = !Objects.equals(previousStatus, nextStatus);
        boolean recurringScheduledAdvanced = action.status() == TaskStatus.SCHEDULED
                && Objects.equals(previousStatus, TaskStatus.SCHEDULED.name())
                && !Objects.equals(existing.getNextFireTime(), resolveNextFireTime(action.status(), action.schedule()));
        if ((terminalAttemptResult && statusChanged) || retryWaitingResult || recurringScheduledAdvanced) {
            return current + 1;
        }
        return current;
    }

    private String resolveLastError(TaskAction action) {
        if (action.status() == TaskStatus.FAILED || action.status() == TaskStatus.TIMEOUT) {
            return limit(action.executionNote(), 1024);
        }
        return null;
    }

    private void copyLockIfStillValid(TaskActionEntity existing, TaskActionEntity next) {
        if (existing == null || existing.getLockedBy() == null) {
            return;
        }
        boolean statusUnchanged = Objects.equals(existing.getStatus(), next.getStatus());
        boolean fireTimeUnchanged = Objects.equals(existing.getNextFireTime(), next.getNextFireTime());
        if (statusUnchanged && fireTimeUnchanged) {
            next.setLockedBy(existing.getLockedBy());
            next.setLockedAt(existing.getLockedAt());
        }
    }

    private TaskPlan toDomain(TaskPlanEntity planEntity, List<TaskActionEntity> actionEntities) {
        List<TaskAction> actions = actionEntities.stream()
                .map(this::toDomainAction)
                .toList();
        return new TaskPlan(
                planEntity.getPlanId(),
                planEntity.getTraceId(),
                planEntity.getSessionId(),
                planEntity.getSourceText(),
                planEntity.getUserId(),
                TaskStatus.valueOf(planEntity.getStatus()),
                actions,
                readWarnings(planEntity.getWarningsJson()),
                planEntity.getCreatedAt(),
                planEntity.getUpdatedAt()
        );
    }

    private TaskAction toDomainAction(TaskActionEntity entity) {
        return new TaskAction(
                entity.getActionId(),
                entity.getActionType(),
                entity.getSkillName(),
                entity.getTitle(),
                entity.getContent(),
                readJson(entity.getTargetJson(), TaskTarget.class, new TaskTarget("unknown", null, null)),
                readJson(entity.getScheduleJson(), TaskSchedule.class, new TaskSchedule("none", null, null, null, "Asia/Shanghai", null, null, 0)),
                readMap(entity.getArgsJson()),
                entity.getPriority(),
                entity.getRiskLevel(),
                entity.getConfidence(),
                entity.getRequiresConfirmation(),
                entity.getSourceSentence(),
                entity.getAnalysisNote(),
                TaskStatus.valueOf(entity.getStatus()),
                entity.getExecutionNote()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON序列化失败", ex);
        }
    }

    private List<String> readWarnings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<ArrayList<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private <T> T readJson(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private OffsetDateTime resolveNextFireTime(TaskStatus status, TaskSchedule schedule) {
        if (status != TaskStatus.SCHEDULED) {
            return null;
        }
        if (schedule == null || !schedule.isScheduled()) {
            return OffsetDateTime.now();
        }
        String effectiveRunAt = schedule.effectiveRunAt();
        if (effectiveRunAt == null || effectiveRunAt.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(effectiveRunAt);
        } catch (DateTimeParseException ex) {
            return OffsetDateTime.now();
        }
    }

    private int sanitizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

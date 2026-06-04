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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class TaskPlanRepository {

    private static final int MAX_DISPATCH_QUERY_LIMIT = 500;
    private static final long DISPATCH_LOCK_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int DEFAULT_RETRY_BACKOFF_SECONDS = 60;
    private static final int MAX_RETRY_BACKOFF_SECONDS = 30 * 60;
    private static final String IDEMPOTENCY_KEY_ARG = "idempotencyKey";

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

        Map<String, TaskActionEntity> existingActions = findActions(plan.planId()).stream()
                .collect(Collectors.toMap(TaskActionEntity::getActionId, Function.identity(), (left, right) -> left));
        Set<String> nextActionIds = new LinkedHashSet<>();

        for (int i = 0; i < plan.tasks().size(); i++) {
            TaskAction action = plan.tasks().get(i);
            nextActionIds.add(action.actionId());
            TaskActionEntity existing = existingActions.get(action.actionId());
            TaskActionEntity entity = toActionEntity(plan.planId(), action, i, existing);
            if (existing == null) {
                taskActionMapper.insert(entity);
            } else {
                taskActionMapper.updateById(entity);
            }
        }

        existingActions.keySet().stream()
                .filter(actionId -> !nextActionIds.contains(actionId))
                .forEach(actionId -> taskActionMapper.delete(new LambdaQueryWrapper<TaskActionEntity>()
                        .eq(TaskActionEntity::getPlanId, plan.planId())
                        .eq(TaskActionEntity::getActionId, actionId)));
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

    public List<TaskPlan> findDueScheduledPlans(OffsetDateTime now, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, MAX_DISPATCH_QUERY_LIMIT));
        long nowEpochMs = now.toInstant().toEpochMilli();
        String nowText = now.toString();

        Set<String> planIds = new LinkedHashSet<>();
        taskActionMapper.selectList(baseDueActionQuery(nowEpochMs)
                        .select(TaskActionEntity::getPlanId)
                        .isNotNull(TaskActionEntity::getNextRunAtEpochMs)
                        .le(TaskActionEntity::getNextRunAtEpochMs, nowEpochMs)
                        .orderByAsc(TaskActionEntity::getNextRunAtEpochMs)
                        .last("limit " + boundedLimit))
                .stream()
                .map(TaskActionEntity::getPlanId)
                .filter(planId -> planId != null && !planId.isBlank())
                .forEach(planIds::add);

        if (planIds.size() < boundedLimit) {
            int remaining = boundedLimit - planIds.size();
            taskActionMapper.selectList(baseDueActionQuery(nowEpochMs)
                            .select(TaskActionEntity::getPlanId)
                            .isNull(TaskActionEntity::getNextRunAtEpochMs)
                            .isNotNull(TaskActionEntity::getNextRunAt)
                            .ne(TaskActionEntity::getNextRunAt, "")
                            .le(TaskActionEntity::getNextRunAt, nowText)
                            .orderByAsc(TaskActionEntity::getNextRunAt)
                            .last("limit " + remaining))
                    .stream()
                    .map(TaskActionEntity::getPlanId)
                    .filter(planId -> planId != null && !planId.isBlank())
                    .forEach(planIds::add);
        }

        return planIds.stream()
                .limit(boundedLimit)
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public boolean tryLockAction(String actionId, String lockedBy, OffsetDateTime now) {
        long nowEpochMs = now.toInstant().toEpochMilli();
        long expiredBefore = nowEpochMs - DISPATCH_LOCK_TIMEOUT_MS;
        TaskActionEntity entity = new TaskActionEntity();
        entity.setLockedBy(lockedBy);
        entity.setLockedAtEpochMs(nowEpochMs);
        int updated = taskActionMapper.update(entity, new LambdaUpdateWrapper<TaskActionEntity>()
                .eq(TaskActionEntity::getActionId, actionId)
                .eq(TaskActionEntity::getStatus, TaskStatus.SCHEDULED.name())
                .and(wrapper -> wrapper.isNull(TaskActionEntity::getLockedBy)
                        .or()
                        .lt(TaskActionEntity::getLockedAtEpochMs, expiredBefore))
                .and(wrapper -> wrapper.isNull(TaskActionEntity::getNextRetryAtEpochMs)
                        .or()
                        .le(TaskActionEntity::getNextRetryAtEpochMs, nowEpochMs))
                .apply("(execution_attempt is null or max_retry_count is null or execution_attempt < max_retry_count)"));
        return updated == 1;
    }

    public void releaseActionLock(String actionId, String lockedBy) {
        taskActionMapper.update(null, new LambdaUpdateWrapper<TaskActionEntity>()
                .set(TaskActionEntity::getLockedBy, null)
                .set(TaskActionEntity::getLockedAtEpochMs, null)
                .eq(TaskActionEntity::getActionId, actionId)
                .eq(TaskActionEntity::getLockedBy, lockedBy));
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

    private LambdaQueryWrapper<TaskActionEntity> baseDueActionQuery(long nowEpochMs) {
        return new LambdaQueryWrapper<TaskActionEntity>()
                .eq(TaskActionEntity::getStatus, TaskStatus.SCHEDULED.name())
                .and(wrapper -> wrapper.isNull(TaskActionEntity::getLockedBy)
                        .or()
                        .lt(TaskActionEntity::getLockedAtEpochMs, nowEpochMs - DISPATCH_LOCK_TIMEOUT_MS))
                .and(wrapper -> wrapper.isNull(TaskActionEntity::getNextRetryAtEpochMs)
                        .or()
                        .le(TaskActionEntity::getNextRetryAtEpochMs, nowEpochMs))
                .apply("(execution_attempt is null or max_retry_count is null or execution_attempt < max_retry_count)");
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
        TaskSchedule schedule = action.schedule();
        String effectiveRunAt = schedule == null ? null : schedule.effectiveRunAt();
        String idempotencyKey = resolveIdempotencyKey(planId, action, schedule, existing);
        int nextExecutionAttempt = resolveNextExecutionAttempt(action, existing);
        int retryBackoffSeconds = resolveRetryBackoffSeconds(action, existing, nextExecutionAttempt);
        entity.setActionId(action.actionId());
        entity.setPlanId(planId);
        entity.setActionType(action.actionType());
        entity.setSkillName(action.skillName());
        entity.setTitle(limit(action.title(), 255));
        entity.setContent(action.content());
        entity.setTargetJson(writeJson(action.target()));
        entity.setScheduleJson(writeJson(schedule));
        entity.setScheduleType(schedule == null ? null : limit(schedule.scheduleType(), 32));
        entity.setRunAt(schedule == null ? null : limit(schedule.runAt(), 64));
        entity.setNextRunAt(limit(effectiveRunAt, 64));
        entity.setNextRunAtEpochMs(toEpochMs(effectiveRunAt));
        entity.setLastRunAt(schedule == null ? null : limit(schedule.lastRunAt(), 64));
        entity.setTriggerCount(schedule == null ? 0 : schedule.triggerCount());
        entity.setExecutionAttempt(nextExecutionAttempt);
        entity.setMaxRetryCount(resolveMaxRetryCount(existing));
        entity.setNextRetryAtEpochMs(resolveNextRetryAtEpochMs(action, existing, retryBackoffSeconds));
        entity.setRetryBackoffSeconds(retryBackoffSeconds);
        entity.setLastErrorMessage(resolveLastErrorMessage(action));
        entity.setIdempotencyKey(idempotencyKey);
        entity.setArgsJson(writeJson(withIdempotencyKey(action.args(), idempotencyKey)));
        entity.setPriority(limit(action.priority(), 32));
        entity.setRiskLevel(limit(action.riskLevel(), 32));
        entity.setConfidence(action.confidence());
        entity.setRequiresConfirmation(action.requiresConfirmation());
        entity.setSourceSentence(action.sourceSentence());
        entity.setAnalysisNote(limit(action.analysisNote(), 512));
        entity.setStatus(action.status().name());
        entity.setExecutionNote(limit(action.executionNote(), 512));
        entity.setSortOrder(sortOrder);
        return entity;
    }

    private int resolveNextExecutionAttempt(TaskAction action, TaskActionEntity existing) {
        int currentAttempt = existing == null || existing.getExecutionAttempt() == null ? 0 : existing.getExecutionAttempt();
        String existingStatus = existing == null ? null : existing.getStatus();
        if (action.status() == TaskStatus.FAILED && !TaskStatus.FAILED.name().equals(existingStatus)) {
            return currentAttempt + 1;
        }
        if (action.status() == TaskStatus.EXECUTED) {
            return 0;
        }
        return currentAttempt;
    }

    private int resolveMaxRetryCount(TaskActionEntity existing) {
        if (existing == null || existing.getMaxRetryCount() == null || existing.getMaxRetryCount() <= 0) {
            return DEFAULT_MAX_RETRY_COUNT;
        }
        return existing.getMaxRetryCount();
    }

    private int resolveRetryBackoffSeconds(TaskAction action, TaskActionEntity existing, int nextExecutionAttempt) {
        if (action.status() == TaskStatus.EXECUTED) {
            return DEFAULT_RETRY_BACKOFF_SECONDS;
        }
        if (action.status() != TaskStatus.FAILED) {
            return existing == null || existing.getRetryBackoffSeconds() == null
                    ? DEFAULT_RETRY_BACKOFF_SECONDS
                    : existing.getRetryBackoffSeconds();
        }
        int previousBackoff = existing == null || existing.getRetryBackoffSeconds() == null || existing.getRetryBackoffSeconds() <= 0
                ? DEFAULT_RETRY_BACKOFF_SECONDS
                : existing.getRetryBackoffSeconds();
        if (nextExecutionAttempt <= 1) {
            return previousBackoff;
        }
        return Math.min(previousBackoff * 2, MAX_RETRY_BACKOFF_SECONDS);
    }

    private Long resolveNextRetryAtEpochMs(TaskAction action, TaskActionEntity existing, int retryBackoffSeconds) {
        if (action.status() == TaskStatus.EXECUTED) {
            return null;
        }
        String existingStatus = existing == null ? null : existing.getStatus();
        if (action.status() == TaskStatus.FAILED && !TaskStatus.FAILED.name().equals(existingStatus)) {
            return OffsetDateTime.now().plusSeconds(retryBackoffSeconds).toInstant().toEpochMilli();
        }
        return existing == null ? null : existing.getNextRetryAtEpochMs();
    }

    private String resolveLastErrorMessage(TaskAction action) {
        if (action.status() == TaskStatus.EXECUTED) {
            return null;
        }
        if (action.status() == TaskStatus.FAILED) {
            return limit(action.executionNote(), 1024);
        }
        return null;
    }

    private String resolveIdempotencyKey(String planId, TaskAction action, TaskSchedule schedule, TaskActionEntity existing) {
        if (existing != null && existing.getIdempotencyKey() != null && !existing.getIdempotencyKey().isBlank()) {
            return existing.getIdempotencyKey();
        }
        Object argValue = action.args().get(IDEMPOTENCY_KEY_ARG);
        if (argValue instanceof String key && !key.isBlank()) {
            return limit(key, 128);
        }
        int triggerCount = schedule == null || schedule.triggerCount() == null ? 0 : schedule.triggerCount();
        return limit(planId + ":" + action.actionId() + ":" + triggerCount, 128);
    }

    private Map<String, Object> withIdempotencyKey(Map<String, Object> args, String idempotencyKey) {
        Map<String, Object> next = args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(args);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            next.putIfAbsent(IDEMPOTENCY_KEY_ARG, idempotencyKey);
        }
        return next;
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
                readSchedule(entity),
                readMapWithIdempotencyKey(entity.getArgsJson(), entity.getIdempotencyKey()),
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

    private TaskSchedule readSchedule(TaskActionEntity entity) {
        TaskSchedule schedule = readJson(entity.getScheduleJson(), TaskSchedule.class, null);
        if (schedule != null) {
            return schedule;
        }
        return new TaskSchedule(
                entity.getScheduleType(),
                null,
                entity.getRunAt(),
                null,
                "Asia/Shanghai",
                entity.getNextRunAt(),
                entity.getLastRunAt(),
                entity.getTriggerCount()
        );
    }

    private Long toEpochMs(String offsetDateTimeText) {
        if (offsetDateTimeText == null || offsetDateTimeText.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(offsetDateTimeText).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
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

    private Map<String, Object> readMapWithIdempotencyKey(String json, String idempotencyKey) {
        Map<String, Object> args = new LinkedHashMap<>(readMap(json));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            args.putIfAbsent(IDEMPOTENCY_KEY_ARG, idempotencyKey);
        }
        return args;
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

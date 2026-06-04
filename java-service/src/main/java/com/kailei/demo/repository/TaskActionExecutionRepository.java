package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.entity.TaskActionExecutionEntity;
import com.kailei.demo.mapper.TaskActionExecutionMapper;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import com.kailei.demo.skill.SkillRetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaskActionExecutionRepository {

    private static final String IDEMPOTENCY_KEY_ARG = "idempotencyKey";
    private static final String RUNNING = "RUNNING";
    private static final int RECOVERY_BATCH_SIZE = 500;

    private final TaskActionExecutionMapper mapper;
    private final ObjectMapper objectMapper;
    private final SkillCatalog skillCatalog;

    public TaskActionExecutionRepository(TaskActionExecutionMapper mapper,
                                         ObjectMapper objectMapper,
                                         SkillCatalog skillCatalog) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.skillCatalog = skillCatalog;
    }

    public ExecutionClaim tryBeginExecution(String planId,
                                            String userId,
                                            SkillDefinition skill,
                                            TaskAction action,
                                            String operatorUserId) {
        String idempotencyKey = idempotencyKey(action);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ExecutionClaim.acquired(null);
        }

        Optional<TaskActionExecutionEntity> existing = findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            TaskActionExecutionEntity entity = existing.get();
            if (TaskStatus.EXECUTED.name().equals(entity.getStatus())) {
                return ExecutionClaim.executed(entity);
            }
            if (RUNNING.equals(entity.getStatus())) {
                int runningTimeoutMinutes = skill.retry().runningTimeoutMinutes();
                if (isRunningExpired(entity, OffsetDateTime.now(), runningTimeoutMinutes)) {
                    recoverStaleRunningExecution(entity.getId(), OffsetDateTime.now(), runningTimeoutMinutes);
                    if (tryMarkRunning(entity.getId(), userId, skill, action, operatorUserId)) {
                        return ExecutionClaim.acquired(entity.getId());
                    }
                    return findByIdempotencyKey(idempotencyKey)
                            .map(this::claimFromExisting)
                            .orElseGet(() -> ExecutionClaim.running(null));
                }
                return ExecutionClaim.running(entity);
            }
            if (tryMarkRunning(entity.getId(), userId, skill, action, operatorUserId)) {
                return ExecutionClaim.acquired(entity.getId());
            }
            return findByIdempotencyKey(idempotencyKey)
                    .map(this::claimFromExisting)
                    .orElseGet(() -> ExecutionClaim.running(null));
        }

        TaskActionExecutionEntity running = buildBaseEntity(
                deterministicId(idempotencyKey),
                planId,
                userId,
                skill,
                action,
                operatorUserId,
                RUNNING
        );
        try {
            mapper.insert(running);
            return ExecutionClaim.acquired(running.getId());
        } catch (DuplicateKeyException ex) {
            return findByIdempotencyKey(idempotencyKey)
                    .map(this::claimFromExisting)
                    .orElseGet(() -> ExecutionClaim.running(null));
        }
    }

    public int recoverStaleRunningExecutions(OffsetDateTime now) {
        List<TaskActionExecutionEntity> runningExecutions = mapper.selectList(new LambdaQueryWrapper<TaskActionExecutionEntity>()
                .eq(TaskActionExecutionEntity::getStatus, RUNNING)
                .orderByAsc(TaskActionExecutionEntity::getUpdatedAt)
                .last("limit " + RECOVERY_BATCH_SIZE));
        int recovered = 0;
        for (TaskActionExecutionEntity entity : runningExecutions) {
            int runningTimeoutMinutes = retryPolicy(entity.getSkillName()).runningTimeoutMinutes();
            if (isRunningExpired(entity, now, runningTimeoutMinutes)
                    && recoverStaleRunningExecution(entity.getId(), now, runningTimeoutMinutes)) {
                recovered++;
            }
        }
        return recovered;
    }

    public Optional<TaskActionExecutionEntity> findExecutedByIdempotencyKey(String idempotencyKey) {
        return findByIdempotencyKey(idempotencyKey)
                .filter(entity -> TaskStatus.EXECUTED.name().equals(entity.getStatus()));
    }

    public Optional<TaskActionExecutionEntity> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<TaskActionExecutionEntity>()
                .eq(TaskActionExecutionEntity::getIdempotencyKey, idempotencyKey)
                .last("limit 1")));
    }

    public TaskActionExecutionEntity record(String planId,
                                            String userId,
                                            SkillDefinition skill,
                                            TaskAction before,
                                            TaskAction after,
                                            String operatorUserId) {
        String idempotencyKey = idempotencyKey(before);
        Optional<TaskActionExecutionEntity> existing = findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            TaskActionExecutionEntity entity = existing.get();
            entity.setStatus(after.status().name());
            entity.setResponsePayload(writeJson(Map.of(
                    "action", after,
                    "executionNote", after.executionNote()
            )));
            entity.setErrorMessage(after.status() == TaskStatus.FAILED ? after.executionNote() : null);
            entity.setOperatorUserId(operatorUserId);
            entity.setUpdatedAt(OffsetDateTime.now());
            mapper.updateById(entity);
            return entity;
        }

        TaskActionExecutionEntity entity = buildBaseEntity(
                fallbackId(idempotencyKey),
                planId,
                userId,
                skill,
                before,
                operatorUserId,
                after.status().name()
        );
        entity.setResponsePayload(writeJson(Map.of(
                "action", after,
                "executionNote", after.executionNote()
        )));
        entity.setErrorMessage(after.status() == TaskStatus.FAILED ? after.executionNote() : null);
        mapper.insert(entity);
        return entity;
    }

    public String idempotencyKey(TaskAction action) {
        if (action == null || action.args() == null) {
            return null;
        }
        Object value = action.args().get(IDEMPOTENCY_KEY_ARG);
        return value == null ? null : String.valueOf(value);
    }

    private boolean tryMarkRunning(String id,
                                   String userId,
                                   SkillDefinition skill,
                                   TaskAction action,
                                   String operatorUserId) {
        TaskActionExecutionEntity update = new TaskActionExecutionEntity();
        update.setStatus(RUNNING);
        update.setSkillName(skill.name());
        update.setRequestPayload(writeJson(Map.of(
                "userId", userId,
                "action", action
        )));
        update.setResponsePayload(null);
        update.setErrorMessage(null);
        update.setOperatorUserId(operatorUserId);
        update.setUpdatedAt(OffsetDateTime.now());
        int updated = mapper.update(update, new LambdaUpdateWrapper<TaskActionExecutionEntity>()
                .eq(TaskActionExecutionEntity::getId, id)
                .ne(TaskActionExecutionEntity::getStatus, TaskStatus.EXECUTED.name())
                .ne(TaskActionExecutionEntity::getStatus, RUNNING));
        return updated == 1;
    }

    private boolean recoverStaleRunningExecution(String id, OffsetDateTime now, int runningTimeoutMinutes) {
        OffsetDateTime expiredBefore = now.minusMinutes(runningTimeoutMinutes);
        TaskActionExecutionEntity update = new TaskActionExecutionEntity();
        update.setStatus(TaskStatus.FAILED.name());
        update.setErrorMessage("RUNNING执行记录超时，已自动恢复为FAILED");
        update.setUpdatedAt(now);
        int updated = mapper.update(update, new LambdaUpdateWrapper<TaskActionExecutionEntity>()
                .eq(TaskActionExecutionEntity::getId, id)
                .eq(TaskActionExecutionEntity::getStatus, RUNNING)
                .lt(TaskActionExecutionEntity::getUpdatedAt, expiredBefore));
        return updated == 1;
    }

    private boolean isRunningExpired(TaskActionExecutionEntity entity, OffsetDateTime now, int runningTimeoutMinutes) {
        return entity.getUpdatedAt() != null && entity.getUpdatedAt().isBefore(now.minusMinutes(runningTimeoutMinutes));
    }

    private SkillRetryPolicy retryPolicy(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return SkillRetryPolicy.defaults();
        }
        return skillCatalog.findByName(skillName)
                .map(SkillDefinition::retry)
                .orElseGet(SkillRetryPolicy::defaults);
    }

    private ExecutionClaim claimFromExisting(TaskActionExecutionEntity entity) {
        if (TaskStatus.EXECUTED.name().equals(entity.getStatus())) {
            return ExecutionClaim.executed(entity);
        }
        return RUNNING.equals(entity.getStatus())
                ? ExecutionClaim.running(entity)
                : ExecutionClaim.acquired(entity.getId());
    }

    private TaskActionExecutionEntity buildBaseEntity(String id,
                                                      String planId,
                                                      String userId,
                                                      SkillDefinition skill,
                                                      TaskAction action,
                                                      String operatorUserId,
                                                      String status) {
        OffsetDateTime now = OffsetDateTime.now();
        TaskActionExecutionEntity entity = new TaskActionExecutionEntity();
        entity.setId(id);
        entity.setPlanId(planId);
        entity.setActionId(action.actionId());
        entity.setIdempotencyKey(idempotencyKey(action));
        entity.setSkillName(skill.name());
        entity.setStatus(status);
        entity.setRequestPayload(writeJson(Map.of(
                "userId", userId,
                "action", action
        )));
        entity.setResponsePayload(null);
        entity.setErrorMessage(null);
        entity.setOperatorUserId(operatorUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private String fallbackId(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return "exec-" + UUID.randomUUID().toString().substring(0, 12);
        }
        return deterministicId(idempotencyKey);
    }

    private String deterministicId(String idempotencyKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return "exec-" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            return "exec-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    public enum ClaimStatus {
        ACQUIRED,
        EXECUTED,
        RUNNING
    }

    public record ExecutionClaim(ClaimStatus status, String executionId, TaskActionExecutionEntity existing) {
        public static ExecutionClaim acquired(String executionId) {
            return new ExecutionClaim(ClaimStatus.ACQUIRED, executionId, null);
        }

        public static ExecutionClaim executed(TaskActionExecutionEntity existing) {
            return new ExecutionClaim(ClaimStatus.EXECUTED, existing == null ? null : existing.getId(), existing);
        }

        public static ExecutionClaim running(TaskActionExecutionEntity existing) {
            return new ExecutionClaim(ClaimStatus.RUNNING, existing == null ? null : existing.getId(), existing);
        }
    }
}

package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kailei.demo.entity.TaskDispatchRecordEntity;
import com.kailei.demo.mapper.TaskDispatchRecordMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaskDispatchRecordRepository {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private final TaskDispatchRecordMapper mapper;

    public TaskDispatchRecordRepository(TaskDispatchRecordMapper mapper) {
        this.mapper = mapper;
    }

    public String buildIdempotencyKey(String planId, String actionId, OffsetDateTime triggerAt) {
        String trigger = triggerAt == null ? "manual" : triggerAt.toString();
        return planId + ":" + actionId + ":" + trigger;
    }

    public Optional<TaskDispatchRecordEntity> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1")));
    }

    public boolean hasSucceeded(String idempotencyKey) {
        return findByIdempotencyKey(idempotencyKey)
                .map(record -> STATUS_SUCCEEDED.equals(record.getStatus()))
                .orElse(false);
    }

    public boolean tryStart(String planId,
                            String actionId,
                            OffsetDateTime triggerAt,
                            String dispatchOwner) {
        String idempotencyKey = buildIdempotencyKey(planId, actionId, triggerAt);
        if (findByIdempotencyKey(idempotencyKey).isPresent()) {
            return false;
        }
        TaskDispatchRecordEntity entity = new TaskDispatchRecordEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.setId("drec-" + UUID.randomUUID().toString().substring(0, 12));
        entity.setPlanId(planId);
        entity.setActionId(actionId);
        entity.setTriggerAt(triggerAt);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setStatus(STATUS_RUNNING);
        entity.setDispatchOwner(dispatchOwner);
        entity.setStartedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.insert(entity) == 1;
    }

    public boolean markSucceeded(String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now();
        return mapper.update(null, new LambdaUpdateWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getIdempotencyKey, idempotencyKey)
                .set(TaskDispatchRecordEntity::getStatus, STATUS_SUCCEEDED)
                .set(TaskDispatchRecordEntity::getFinishedAt, now)
                .set(TaskDispatchRecordEntity::getUpdatedAt, now)
                .set(TaskDispatchRecordEntity::getErrorMessage, null)) == 1;
    }

    public boolean markFailed(String idempotencyKey, String errorMessage) {
        OffsetDateTime now = OffsetDateTime.now();
        return mapper.update(null, new LambdaUpdateWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getIdempotencyKey, idempotencyKey)
                .set(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .set(TaskDispatchRecordEntity::getFinishedAt, now)
                .set(TaskDispatchRecordEntity::getUpdatedAt, now)
                .set(TaskDispatchRecordEntity::getErrorMessage, limit(errorMessage, 1024))) == 1;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

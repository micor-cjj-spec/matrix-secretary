package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kailei.demo.entity.TaskDispatchRecordEntity;
import com.kailei.demo.mapper.TaskDispatchRecordMapper;
import com.kailei.demo.model.PageResult;
import com.kailei.demo.model.TaskDispatchSummaryResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaskDispatchRecordRepository {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private static final long DEFAULT_RECOVERY_BATCH_SIZE = 50;
    private static final long MAX_RECOVERY_BATCH_SIZE = 500;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final long DEFAULT_RETRY_BACKOFF_SECONDS = 60;

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

    public PageResult<TaskDispatchRecordEntity> findByPlanId(String planId, Long page, Long size) {
        return findByPlanId(planId, null, null, null, null, page, size);
    }

    public PageResult<TaskDispatchRecordEntity> findByPlanId(String planId,
                                                             String status,
                                                             OffsetDateTime startTime,
                                                             OffsetDateTime endTime,
                                                             String dispatchOwner,
                                                             Long page,
                                                             Long size) {
        LambdaQueryWrapper<TaskDispatchRecordEntity> wrapper = new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getPlanId, planId);
        applyQueryFilters(wrapper, status, startTime, endTime, dispatchOwner);
        applyDefaultOrder(wrapper);
        return toPageResult(mapper.selectPage(
                new Page<>(PageResult.normalizePage(page), PageResult.normalizeSize(size)),
                wrapper
        ));
    }

    public PageResult<TaskDispatchRecordEntity> findByPlanIdAndActionId(String planId,
                                                                        String actionId,
                                                                        Long page,
                                                                        Long size) {
        return findByPlanIdAndActionId(planId, actionId, null, null, null, null, page, size);
    }

    public PageResult<TaskDispatchRecordEntity> findByPlanIdAndActionId(String planId,
                                                                        String actionId,
                                                                        String status,
                                                                        OffsetDateTime startTime,
                                                                        OffsetDateTime endTime,
                                                                        String dispatchOwner,
                                                                        Long page,
                                                                        Long size) {
        LambdaQueryWrapper<TaskDispatchRecordEntity> wrapper = new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getPlanId, planId)
                .eq(TaskDispatchRecordEntity::getActionId, actionId);
        applyQueryFilters(wrapper, status, startTime, endTime, dispatchOwner);
        applyDefaultOrder(wrapper);
        return toPageResult(mapper.selectPage(
                new Page<>(PageResult.normalizePage(page), PageResult.normalizeSize(size)),
                wrapper
        ));
    }

    public TaskDispatchSummaryResponse summarizeByPlanId(String planId) {
        long total = countByPlanId(planId);
        long running = countByPlanIdAndStatus(planId, STATUS_RUNNING);
        long succeeded = countByPlanIdAndStatus(planId, STATUS_SUCCEEDED);
        long failed = countByPlanIdAndStatus(planId, STATUS_FAILED);
        long retryScheduled = countRetryScheduledByPlanId(planId);
        long retryExhausted = countRetryExhaustedByPlanId(planId);
        Optional<TaskDispatchRecordEntity> latest = findLatestByPlanId(planId);
        return new TaskDispatchSummaryResponse(
                total,
                running,
                succeeded,
                failed,
                retryScheduled,
                retryExhausted,
                TaskDispatchSummaryResponse.successRate(total, succeeded),
                latest.map(TaskDispatchRecordEntity::getStatus).orElse(null),
                latest.map(TaskDispatchRecordEntity::getTriggerAt).orElse(null),
                latest.map(TaskDispatchRecordEntity::getFinishedAt).orElse(null)
        );
    }

    public Optional<TaskDispatchRecordEntity> findLatestByPlanId(String planId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getPlanId, planId)
                .orderByDesc(TaskDispatchRecordEntity::getTriggerAt)
                .orderByDesc(TaskDispatchRecordEntity::getCreatedAt)
                .last("LIMIT 1")));
    }

    public long countRunningRecords() {
        return countByStatus(STATUS_RUNNING);
    }

    public long countFailedRecords() {
        return countByStatus(STATUS_FAILED);
    }

    public long countRetryScheduledRecords() {
        return mapper.selectCount(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .isNotNull(TaskDispatchRecordEntity::getNextRetryAt)
                .apply("COALESCE(retry_count, 0) < COALESCE(max_retry_count, 0)"));
    }

    public long countRetryExhaustedRecords() {
        return mapper.selectCount(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .apply("COALESCE(retry_count, 0) >= COALESCE(max_retry_count, 0)"));
    }

    private long countByPlanId(String planId) {
        return mapper.selectCount(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getPlanId, planId));
    }

    private long countByPlanIdAndStatus(String planId, String status) {
        return mapper.selectCount(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getPlanId, planId)
                .eq(TaskDispatchRecordEntity::getStatus, status));
    }

    private long countByStatus(String status) {
        return mapper.selectCount(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getStatus, status));
    }

    private long countRetryScheduledByPlanId(String planId) {
        return mapper.selectCount(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getPlanId, planId)
                .eq(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .isNotNull(TaskDispatchRecordEntity::getNextRetryAt)
                .apply("COALESCE(retry_count, 0) < COALESCE(max_retry_count, 0)"));
    }

    private long countRetryExhaustedByPlanId(String planId) {
        return mapper.selectCount(new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getPlanId, planId)
                .eq(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .apply("COALESCE(retry_count, 0) >= COALESCE(max_retry_count, 0)"));
    }

    private void applyQueryFilters(LambdaQueryWrapper<TaskDispatchRecordEntity> wrapper,
                                   String status,
                                   OffsetDateTime startTime,
                                   OffsetDateTime endTime,
                                   String dispatchOwner) {
        if (status != null && !status.isBlank()) {
            wrapper.eq(TaskDispatchRecordEntity::getStatus, status.trim().toUpperCase());
        }
        if (startTime != null) {
            wrapper.ge(TaskDispatchRecordEntity::getTriggerAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(TaskDispatchRecordEntity::getTriggerAt, endTime);
        }
        if (dispatchOwner != null && !dispatchOwner.isBlank()) {
            wrapper.eq(TaskDispatchRecordEntity::getDispatchOwner, dispatchOwner.trim());
        }
    }

    private void applyDefaultOrder(LambdaQueryWrapper<TaskDispatchRecordEntity> wrapper) {
        wrapper.orderByDesc(TaskDispatchRecordEntity::getTriggerAt)
                .orderByDesc(TaskDispatchRecordEntity::getCreatedAt);
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
        return tryStart(planId, actionId, triggerAt, dispatchOwner, DEFAULT_MAX_RETRY_COUNT);
    }

    public boolean tryStart(String planId,
                            String actionId,
                            OffsetDateTime triggerAt,
                            String dispatchOwner,
                            Integer maxRetryCount) {
        TaskDispatchRecordEntity entity = newRunningRecord(planId, actionId, triggerAt, dispatchOwner, normalizeMaxRetryCount(maxRetryCount));
        try {
            return mapper.insert(entity) == 1;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    public boolean tryStartOrRetry(String planId,
                                   String actionId,
                                   OffsetDateTime triggerAt,
                                   String dispatchOwner,
                                   Integer maxRetryCount) {
        if (tryStart(planId, actionId, triggerAt, dispatchOwner, maxRetryCount)) {
            return true;
        }
        return tryRestartFailedForRetry(
                buildIdempotencyKey(planId, actionId, triggerAt),
                dispatchOwner,
                maxRetryCount
        );
    }

    public boolean tryRestartFailedForRetry(String idempotencyKey,
                                            String dispatchOwner,
                                            Integer maxRetryCount) {
        int normalizedMaxRetryCount = normalizeMaxRetryCount(maxRetryCount);
        OffsetDateTime now = OffsetDateTime.now();
        return mapper.update(null, new LambdaUpdateWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getIdempotencyKey, idempotencyKey)
                .eq(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .apply("COALESCE(retry_count, 0) < {0}", normalizedMaxRetryCount)
                .and(retry -> retry.isNull(TaskDispatchRecordEntity::getNextRetryAt)
                        .or()
                        .le(TaskDispatchRecordEntity::getNextRetryAt, now))
                .set(TaskDispatchRecordEntity::getStatus, STATUS_RUNNING)
                .set(TaskDispatchRecordEntity::getDispatchOwner, dispatchOwner)
                .set(TaskDispatchRecordEntity::getStartedAt, now)
                .set(TaskDispatchRecordEntity::getFinishedAt, null)
                .set(TaskDispatchRecordEntity::getUpdatedAt, now)
                .set(TaskDispatchRecordEntity::getErrorMessage, null)
                .set(TaskDispatchRecordEntity::getMaxRetryCount, normalizedMaxRetryCount)
                .set(TaskDispatchRecordEntity::getNextRetryAt, null)
                .setSql("retry_count = COALESCE(retry_count, 0) + 1")) == 1;
    }

    private TaskDispatchRecordEntity newRunningRecord(String planId,
                                                      String actionId,
                                                      OffsetDateTime triggerAt,
                                                      String dispatchOwner,
                                                      int maxRetryCount) {
        TaskDispatchRecordEntity entity = new TaskDispatchRecordEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.setId("drec-" + UUID.randomUUID().toString().substring(0, 12));
        entity.setPlanId(planId);
        entity.setActionId(actionId);
        entity.setTriggerAt(triggerAt);
        entity.setIdempotencyKey(buildIdempotencyKey(planId, actionId, triggerAt));
        entity.setStatus(STATUS_RUNNING);
        entity.setDispatchOwner(dispatchOwner);
        entity.setStartedAt(now);
        entity.setRetryCount(0);
        entity.setMaxRetryCount(maxRetryCount);
        entity.setNextRetryAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    public List<TaskDispatchRecordEntity> findTimedOutRunningRecords(OffsetDateTime timeoutBefore, Long batchSize) {
        long normalizedBatchSize = normalizeBatchSize(batchSize);
        return mapper.selectPage(new Page<>(1, normalizedBatchSize), new LambdaQueryWrapper<TaskDispatchRecordEntity>()
                        .eq(TaskDispatchRecordEntity::getStatus, STATUS_RUNNING)
                        .isNotNull(TaskDispatchRecordEntity::getStartedAt)
                        .le(TaskDispatchRecordEntity::getStartedAt, timeoutBefore)
                        .orderByAsc(TaskDispatchRecordEntity::getStartedAt))
                .getRecords();
    }

    public boolean markTimedOutAsFailed(String id, String errorMessage) {
        return markTimedOutAsFailed(id, errorMessage, DEFAULT_RETRY_BACKOFF_SECONDS);
    }

    public boolean markTimedOutAsFailed(String id, String errorMessage, Long retryBackoffSeconds) {
        OffsetDateTime now = OffsetDateTime.now();
        return mapper.update(null, new LambdaUpdateWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getId, id)
                .eq(TaskDispatchRecordEntity::getStatus, STATUS_RUNNING)
                .set(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .set(TaskDispatchRecordEntity::getFinishedAt, now)
                .set(TaskDispatchRecordEntity::getUpdatedAt, now)
                .set(TaskDispatchRecordEntity::getNextRetryAt, now.plusSeconds(normalizeRetryBackoffSeconds(retryBackoffSeconds)))
                .set(TaskDispatchRecordEntity::getErrorMessage, limit(errorMessage, 1024))) == 1;
    }

    public int markTimedOutRunningRecordsAsFailed(OffsetDateTime timeoutBefore, Long batchSize, String errorMessage) {
        return markTimedOutRunningRecordsAsFailed(timeoutBefore, batchSize, errorMessage, DEFAULT_RETRY_BACKOFF_SECONDS);
    }

    public int markTimedOutRunningRecordsAsFailed(OffsetDateTime timeoutBefore,
                                                  Long batchSize,
                                                  String errorMessage,
                                                  Long retryBackoffSeconds) {
        List<TaskDispatchRecordEntity> timedOutRecords = findTimedOutRunningRecords(timeoutBefore, batchSize);
        int recovered = 0;
        for (TaskDispatchRecordEntity record : timedOutRecords) {
            if (markTimedOutAsFailed(record.getId(), errorMessage, retryBackoffSeconds)) {
                recovered++;
            }
        }
        return recovered;
    }

    public boolean markSucceeded(String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now();
        return mapper.update(null, new LambdaUpdateWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getIdempotencyKey, idempotencyKey)
                .set(TaskDispatchRecordEntity::getStatus, STATUS_SUCCEEDED)
                .set(TaskDispatchRecordEntity::getFinishedAt, now)
                .set(TaskDispatchRecordEntity::getUpdatedAt, now)
                .set(TaskDispatchRecordEntity::getNextRetryAt, null)
                .set(TaskDispatchRecordEntity::getErrorMessage, null)) == 1;
    }

    public boolean markFailed(String idempotencyKey, String errorMessage) {
        return markFailed(idempotencyKey, errorMessage, DEFAULT_RETRY_BACKOFF_SECONDS);
    }

    public boolean markFailed(String idempotencyKey, String errorMessage, Long retryBackoffSeconds) {
        OffsetDateTime now = OffsetDateTime.now();
        return mapper.update(null, new LambdaUpdateWrapper<TaskDispatchRecordEntity>()
                .eq(TaskDispatchRecordEntity::getIdempotencyKey, idempotencyKey)
                .set(TaskDispatchRecordEntity::getStatus, STATUS_FAILED)
                .set(TaskDispatchRecordEntity::getFinishedAt, now)
                .set(TaskDispatchRecordEntity::getUpdatedAt, now)
                .set(TaskDispatchRecordEntity::getNextRetryAt, now.plusSeconds(normalizeRetryBackoffSeconds(retryBackoffSeconds)))
                .set(TaskDispatchRecordEntity::getErrorMessage, limit(errorMessage, 1024))) == 1;
    }

    private PageResult<TaskDispatchRecordEntity> toPageResult(Page<TaskDispatchRecordEntity> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private long normalizeBatchSize(Long batchSize) {
        if (batchSize == null || batchSize < 1) {
            return DEFAULT_RECOVERY_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_RECOVERY_BATCH_SIZE);
    }

    private int normalizeMaxRetryCount(Integer maxRetryCount) {
        if (maxRetryCount == null || maxRetryCount < 0) {
            return DEFAULT_MAX_RETRY_COUNT;
        }
        return maxRetryCount;
    }

    private long normalizeRetryBackoffSeconds(Long retryBackoffSeconds) {
        if (retryBackoffSeconds == null || retryBackoffSeconds < 1) {
            return DEFAULT_RETRY_BACKOFF_SECONDS;
        }
        return retryBackoffSeconds;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

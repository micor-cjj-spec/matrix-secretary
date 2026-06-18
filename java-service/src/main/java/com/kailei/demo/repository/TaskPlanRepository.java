package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.entity.TaskActionEntity;
import com.kailei.demo.entity.TaskPlanEntity;
import com.kailei.demo.mapper.TaskActionMapper;
import com.kailei.demo.mapper.TaskPlanMapper;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskPlanPageResponse;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.model.TaskTarget;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class TaskPlanRepository {

    private static final int MAX_PAGE_SIZE = 100;

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
        syncActions(plan);
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

    public TaskPlanPageResponse findPage(String userId, String status, String sessionId, Integer pageNo, Integer pageSize) {
        int safePageNo = sanitizePageNo(pageNo);
        int safePageSize = sanitizePageSize(pageSize);
        long total = taskPlanMapper.selectCount(buildPlanQuery(userId, status, sessionId));
        long offset = (long) (safePageNo - 1) * safePageSize;
        List<TaskPlan> records = taskPlanMapper.selectList(buildPlanQuery(userId, status, sessionId)
                        .orderByDesc(TaskPlanEntity::getCreatedAt)
                        .last("limit " + safePageSize + " offset " + offset))
                .stream()
                .map(planEntity -> toDomain(planEntity, findActions(planEntity.getPlanId())))
                .toList();
        return new TaskPlanPageResponse(safePageNo, safePageSize, total, records);
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

    private void syncActions(TaskPlan plan) {
        Set<String> currentActionIds = new LinkedHashSet<>();
        for (int i = 0; i < plan.tasks().size(); i++) {
            TaskActionEntity actionEntity = toActionEntity(plan.planId(), plan.tasks().get(i), i);
            currentActionIds.add(actionEntity.getActionId());
            if (taskActionMapper.updateById(actionEntity) == 0) {
                taskActionMapper.insert(actionEntity);
            }
        }
        pruneRemovedActions(plan.planId(), currentActionIds);
    }

    private void pruneRemovedActions(String planId, Set<String> currentActionIds) {
        LambdaQueryWrapper<TaskActionEntity> wrapper = new LambdaQueryWrapper<TaskActionEntity>()
                .eq(TaskActionEntity::getPlanId, planId);
        if (!currentActionIds.isEmpty()) {
            wrapper.notIn(TaskActionEntity::getActionId, currentActionIds);
        }
        taskActionMapper.delete(wrapper);
    }

    private LambdaQueryWrapper<TaskPlanEntity> buildPlanQuery(String userId, String status, String sessionId) {
        LambdaQueryWrapper<TaskPlanEntity> wrapper = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(TaskPlanEntity::getUserId, userId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(TaskPlanEntity::getStatus, status.trim().toUpperCase());
        }
        if (sessionId != null && !sessionId.isBlank()) {
            wrapper.eq(TaskPlanEntity::getSessionId, sessionId);
        }
        return wrapper;
    }

    private int sanitizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int sanitizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
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

    private TaskActionEntity toActionEntity(String planId, TaskAction action, int sortOrder) {
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
        return entity;
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

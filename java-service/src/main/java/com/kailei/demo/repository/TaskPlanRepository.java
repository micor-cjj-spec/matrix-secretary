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
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.model.TaskTarget;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TaskPlanRepository {

    private static final int MAX_DISPATCH_QUERY_LIMIT = 500;

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
        taskActionMapper.delete(new LambdaQueryWrapper<TaskActionEntity>()
                .eq(TaskActionEntity::getPlanId, plan.planId()));
        for (int i = 0; i < plan.tasks().size(); i++) {
            taskActionMapper.insert(toActionEntity(plan.planId(), plan.tasks().get(i), i));
        }
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
        List<String> planIds = taskActionMapper.selectList(new LambdaQueryWrapper<TaskActionEntity>()
                        .select(TaskActionEntity::getPlanId)
                        .eq(TaskActionEntity::getStatus, TaskStatus.SCHEDULED.name())
                        .groupBy(TaskActionEntity::getPlanId)
                        .last("limit " + boundedLimit))
                .stream()
                .map(TaskActionEntity::getPlanId)
                .filter(planId -> planId != null && !planId.isBlank())
                .toList();

        return planIds.stream()
                .map(this::findById)
                .flatMap(Optional::stream)
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

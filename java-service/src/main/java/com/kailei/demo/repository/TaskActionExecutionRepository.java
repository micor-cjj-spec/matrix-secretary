package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.entity.TaskActionExecutionEntity;
import com.kailei.demo.mapper.TaskActionExecutionMapper;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.skill.SkillDefinition;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaskActionExecutionRepository {

    private static final String IDEMPOTENCY_KEY_ARG = "idempotencyKey";

    private final TaskActionExecutionMapper mapper;
    private final ObjectMapper objectMapper;

    public TaskActionExecutionRepository(TaskActionExecutionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Optional<TaskActionExecutionEntity> findExecutedByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<TaskActionExecutionEntity>()
                .eq(TaskActionExecutionEntity::getIdempotencyKey, idempotencyKey)
                .eq(TaskActionExecutionEntity::getStatus, TaskStatus.EXECUTED.name())
                .last("limit 1")));
    }

    public TaskActionExecutionEntity record(String planId,
                                            String userId,
                                            SkillDefinition skill,
                                            TaskAction before,
                                            TaskAction after,
                                            String operatorUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        TaskActionExecutionEntity entity = new TaskActionExecutionEntity();
        entity.setId("exec-" + UUID.randomUUID().toString().substring(0, 12));
        entity.setPlanId(planId);
        entity.setActionId(before.actionId());
        entity.setIdempotencyKey(idempotencyKey(before));
        entity.setSkillName(skill.name());
        entity.setStatus(after.status().name());
        entity.setRequestPayload(writeJson(Map.of(
                "userId", userId,
                "action", before
        )));
        entity.setResponsePayload(writeJson(Map.of(
                "action", after,
                "executionNote", after.executionNote()
        )));
        entity.setErrorMessage(after.status() == TaskStatus.FAILED ? after.executionNote() : null);
        entity.setOperatorUserId(operatorUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}

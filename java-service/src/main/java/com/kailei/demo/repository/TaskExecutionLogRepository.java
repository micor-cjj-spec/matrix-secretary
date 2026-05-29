package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.entity.TaskExecutionLogEntity;
import com.kailei.demo.mapper.TaskExecutionLogMapper;
import com.kailei.demo.model.TaskAction;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class TaskExecutionLogRepository {

    private final TaskExecutionLogMapper mapper;
    private final ObjectMapper objectMapper;

    public TaskExecutionLogRepository(TaskExecutionLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void log(String planId,
                    TaskAction before,
                    TaskAction after,
                    String operatorUserId,
                    Map<String, Object> requestPayload,
                    Map<String, Object> responsePayload,
                    String errorMessage) {
        TaskExecutionLogEntity entity = new TaskExecutionLogEntity();
        entity.setId("elog-" + UUID.randomUUID().toString().substring(0, 12));
        entity.setPlanId(planId);
        entity.setActionId(after.actionId());
        entity.setSkillName(after.skillName());
        entity.setStatus(after.status().name());
        entity.setRequestPayload(writeJson(requestPayload == null ? Map.of() : requestPayload));
        entity.setResponsePayload(writeJson(responsePayload == null ? Map.of() : responsePayload));
        entity.setErrorMessage(limit(errorMessage, 1024));
        entity.setOperatorUserId(operatorUserId);
        entity.setCreatedAt(OffsetDateTime.now());
        mapper.insert(entity);
    }

    public void logStateChange(String planId, TaskAction before, TaskAction after, String operatorUserId) {
        log(
                planId,
                before,
                after,
                operatorUserId,
                Map.of(
                        "actionId", before.actionId(),
                        "status", before.status().name(),
                        "content", before.content(),
                        "schedule", before.schedule() == null ? Map.of() : before.schedule()
                ),
                Map.of(
                        "actionId", after.actionId(),
                        "status", after.status().name(),
                        "executionNote", after.executionNote() == null ? "" : after.executionNote(),
                        "schedule", after.schedule() == null ? Map.of() : after.schedule()
                ),
                after.status().name().equals("FAILED") ? after.executionNote() : null
        );
    }

    public List<TaskExecutionLogEntity> findByPlanId(String planId) {
        return mapper.selectList(new LambdaQueryWrapper<TaskExecutionLogEntity>()
                .eq(TaskExecutionLogEntity::getPlanId, planId)
                .orderByDesc(TaskExecutionLogEntity::getCreatedAt));
    }

    public List<TaskExecutionLogEntity> findByPlanIdAndActionId(String planId, String actionId) {
        return mapper.selectList(new LambdaQueryWrapper<TaskExecutionLogEntity>()
                .eq(TaskExecutionLogEntity::getPlanId, planId)
                .eq(TaskExecutionLogEntity::getActionId, actionId)
                .orderByDesc(TaskExecutionLogEntity::getCreatedAt));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

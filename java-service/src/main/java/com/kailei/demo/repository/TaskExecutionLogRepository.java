package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.entity.TaskExecutionLogEntity;
import com.kailei.demo.mapper.TaskExecutionLogMapper;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
public class TaskExecutionLogRepository {

    private static final String EVENT_EDIT = "EDIT";
    private static final String EVENT_SCHEDULE = "SCHEDULE";
    private static final String EVENT_EXECUTE = "EXECUTE";
    private static final String EVENT_FAIL = "FAIL";
    private static final String EVENT_CANCEL = "CANCEL";
    private static final String EVENT_STATE_CHANGE = "STATE_CHANGE";

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
        log(planId, null, before, after, operatorUserId, requestPayload, responsePayload, errorMessage);
    }

    public void log(String planId,
                    String traceId,
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
        entity.setEventType(resolveEventType(before, after));
        entity.setStatus(after.status().name());
        entity.setBeforeStatus(before == null ? null : before.status().name());
        entity.setAfterStatus(after.status().name());
        entity.setRequestPayload(writeJson(requestPayload == null ? Map.of() : requestPayload));
        entity.setResponsePayload(writeJson(responsePayload == null ? Map.of() : responsePayload));
        entity.setDiffJson(writeJson(buildDiff(before, after)));
        entity.setErrorMessage(limit(errorMessage, 1024));
        entity.setOperatorUserId(operatorUserId);
        entity.setOperatorType(resolveOperatorType(operatorUserId));
        entity.setTraceId(traceId);
        entity.setCreatedAt(OffsetDateTime.now());
        mapper.insert(entity);
    }

    public void logStateChange(String planId, TaskAction before, TaskAction after, String operatorUserId) {
        logStateChange(planId, null, before, after, operatorUserId);
    }

    public void logStateChange(String planId, String traceId, TaskAction before, TaskAction after, String operatorUserId) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("actionId", before.actionId());
        requestPayload.put("status", before.status().name());
        requestPayload.put("content", before.content());
        requestPayload.put("schedule", before.schedule());

        Map<String, Object> responsePayload = new LinkedHashMap<>();
        responsePayload.put("actionId", after.actionId());
        responsePayload.put("status", after.status().name());
        responsePayload.put("executionNote", after.executionNote());
        responsePayload.put("schedule", after.schedule());

        log(
                planId,
                traceId,
                before,
                after,
                operatorUserId,
                requestPayload,
                responsePayload,
                after.status() == TaskStatus.FAILED ? after.executionNote() : null
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

    private String resolveEventType(TaskAction before, TaskAction after) {
        if (after == null || after.status() == null) {
            return EVENT_STATE_CHANGE;
        }
        if (before != null
                && before.status() == TaskStatus.WAITING_CONFIRM
                && after.status() == TaskStatus.WAITING_CONFIRM
                && !buildDiff(before, after).isEmpty()) {
            return EVENT_EDIT;
        }
        if (after.status() == TaskStatus.SCHEDULED) {
            return EVENT_SCHEDULE;
        }
        if (after.status() == TaskStatus.EXECUTED) {
            return EVENT_EXECUTE;
        }
        if (after.status() == TaskStatus.FAILED) {
            return EVENT_FAIL;
        }
        if (after.status() == TaskStatus.CANCELLED) {
            return EVENT_CANCEL;
        }
        return EVENT_STATE_CHANGE;
    }

    private String resolveOperatorType(String operatorUserId) {
        if (operatorUserId == null || operatorUserId.isBlank()) {
            return "UNKNOWN";
        }
        if (operatorUserId.startsWith("system-")) {
            return "SYSTEM";
        }
        return "USER";
    }

    private Map<String, Object> buildDiff(TaskAction before, TaskAction after) {
        Map<String, Object> diff = new LinkedHashMap<>();
        if (before == null || after == null) {
            return diff;
        }
        putDiff(diff, "title", before.title(), after.title());
        putDiff(diff, "content", before.content(), after.content());
        putDiff(diff, "target", before.target(), after.target());
        putDiff(diff, "schedule", before.schedule(), after.schedule());
        putDiff(diff, "args", before.args(), after.args());
        putDiff(diff, "priority", before.priority(), after.priority());
        putDiff(diff, "requiresConfirmation", before.requiresConfirmation(), after.requiresConfirmation());
        putDiff(diff, "status", before.status(), after.status());
        putDiff(diff, "executionNote", before.executionNote(), after.executionNote());
        return diff;
    }

    private void putDiff(Map<String, Object> diff, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("before", before);
        item.put("after", after);
        diff.put(field, item);
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

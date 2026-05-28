package com.kailei.demo.skill;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskTarget;

import java.util.Collection;
import java.util.Map;

public class SkillArgumentValidator {

    public void validate(SkillDefinition skill, TaskAction action) {
        Map<String, Object> schema = skill.inputSchema();
        Object requiredRaw = schema.get("required");
        if (!(requiredRaw instanceof Collection<?> requiredFields)) {
            return;
        }
        for (Object item : requiredFields) {
            String field = String.valueOf(item);
            if (isMissing(field, action)) {
                throw new IllegalArgumentException("Skill[" + skill.name() + "] 缺少必填参数: " + field);
            }
        }
    }

    private boolean isMissing(String field, TaskAction action) {
        return switch (field) {
            case "content" -> isBlank(action.content());
            case "target" -> isMissingTarget(action.target());
            case "schedule" -> action.schedule() == null;
            default -> action.args() == null || isEmpty(action.args().get(field));
        };
    }

    private boolean isMissingTarget(TaskTarget target) {
        if (target == null) {
            return true;
        }
        return isBlank(target.name()) && isBlank(target.address()) && (target.targetType() == null || "unknown".equalsIgnoreCase(target.targetType()));
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return isBlank(text);
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

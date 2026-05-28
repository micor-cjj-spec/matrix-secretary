package com.kailei.demo.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record TaskAction(
        String actionId,
        String actionType,
        String skillName,
        String title,
        String content,
        TaskTarget target,
        TaskSchedule schedule,
        Map<String, Object> args,
        String priority,
        String riskLevel,
        Double confidence,
        Boolean requiresConfirmation,
        String sourceSentence,
        String analysisNote,
        TaskStatus status,
        String executionNote
) {
    public TaskAction {
        args = args == null ? Map.of() : new LinkedHashMap<>(args);
        riskLevel = riskLevel == null || riskLevel.isBlank() ? "LOW" : riskLevel;
        requiresConfirmation = Boolean.TRUE.equals(requiresConfirmation);
    }

    public TaskAction withStatus(TaskStatus nextStatus, String note) {
        return new TaskAction(
                actionId,
                actionType,
                skillName,
                title,
                content,
                target,
                schedule,
                args,
                priority,
                riskLevel,
                confidence,
                requiresConfirmation,
                sourceSentence,
                analysisNote,
                nextStatus,
                note
        );
    }
}

package com.kailei.demo.skill;

import java.util.List;
import java.util.Map;

public record SkillDefinition(
        String name,
        String displayName,
        String description,
        List<String> triggerKeywords,
        String actionType,
        String riskLevel,
        Boolean requiresConfirmation,
        Map<String, Object> inputSchema,
        SkillExecution execution,
        SkillRetryPolicy retry
) {

    public SkillDefinition {
        triggerKeywords = triggerKeywords == null ? List.of() : List.copyOf(triggerKeywords);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        riskLevel = riskLevel == null || riskLevel.isBlank() ? "LOW" : riskLevel;
        requiresConfirmation = Boolean.TRUE.equals(requiresConfirmation);
        retry = retry == null ? SkillRetryPolicy.defaults() : retry;
    }

    public boolean supportsActionType(String candidateActionType) {
        return actionType != null && actionType.equals(candidateActionType);
    }

    public static SkillDefinition unknown(String actionType) {
        return new SkillDefinition(
                "unknown",
                "未知能力",
                "未在 SkillCatalog 中注册的能力。",
                List.of(),
                actionType,
                "HIGH",
                true,
                Map.of(),
                new SkillExecution("noop", null, null, null, Map.of(), Map.of(), Map.of()),
                SkillRetryPolicy.defaults()
        );
    }
}

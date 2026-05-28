package com.kailei.demo.model;

public record TaskAction(
        String actionId,
        String actionType,
        String title,
        String content,
        TaskTarget target,
        TaskSchedule schedule,
        String priority,
        Double confidence,
        Boolean requiresConfirmation,
        String sourceSentence,
        String analysisNote,
        TaskStatus status,
        String executionNote
) {
    public TaskAction withStatus(TaskStatus nextStatus, String note) {
        return new TaskAction(
                actionId,
                actionType,
                title,
                content,
                target,
                schedule,
                priority,
                confidence,
                requiresConfirmation,
                sourceSentence,
                analysisNote,
                nextStatus,
                note
        );
    }
}

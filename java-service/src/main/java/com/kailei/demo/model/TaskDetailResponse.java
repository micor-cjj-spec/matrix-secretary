package com.kailei.demo.model;

import com.kailei.demo.entity.TaskExecutionLogEntity;

import java.util.List;

public record TaskDetailResponse(
        TaskPlan plan,
        List<TaskExecutionLogEntity> recentExecutionLogs,
        List<TaskDispatchRecordResponse> recentDispatchRecords,
        TaskDispatchSummaryResponse dispatchSummary
) {
    public TaskDetailResponse {
        recentExecutionLogs = recentExecutionLogs == null ? List.of() : List.copyOf(recentExecutionLogs);
        recentDispatchRecords = recentDispatchRecords == null ? List.of() : List.copyOf(recentDispatchRecords);
        dispatchSummary = dispatchSummary == null ? new TaskDispatchSummaryResponse(0, 0, 0, 0) : dispatchSummary;
    }
}

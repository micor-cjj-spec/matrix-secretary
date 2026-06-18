package com.kailei.demo.model;

import java.util.List;

public record TaskPlanPageResponse(
        int pageNo,
        int pageSize,
        long total,
        List<TaskPlan> records
) {
}

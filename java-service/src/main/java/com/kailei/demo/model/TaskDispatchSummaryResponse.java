package com.kailei.demo.model;

import java.time.OffsetDateTime;

public record TaskDispatchSummaryResponse(
        long total,
        long running,
        long succeeded,
        long failed,
        long retryScheduled,
        long retryExhausted,
        double successRate,
        String latestStatus,
        OffsetDateTime latestTriggerAt,
        OffsetDateTime latestFinishedAt
) {
    public TaskDispatchSummaryResponse(long total, long running, long succeeded, long failed) {
        this(total, running, succeeded, failed, 0, 0, successRate(total, succeeded), null, null, null);
    }

    public static double successRate(long total, long succeeded) {
        if (total <= 0) {
            return 0.0;
        }
        return succeeded * 100.0 / total;
    }
}

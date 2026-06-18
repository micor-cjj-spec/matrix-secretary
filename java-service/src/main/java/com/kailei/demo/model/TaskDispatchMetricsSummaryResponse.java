package com.kailei.demo.model;

import java.time.OffsetDateTime;

public record TaskDispatchMetricsSummaryResponse(
        long runningCurrent,
        long failedCurrent,
        long retryScheduledCurrent,
        long retryExhaustedCurrent,
        double startedTotal,
        double succeededTotal,
        double failedTotal,
        double retryStartedTotal,
        double timeoutRecoveredTotal,
        OffsetDateTime generatedAt
) {
}

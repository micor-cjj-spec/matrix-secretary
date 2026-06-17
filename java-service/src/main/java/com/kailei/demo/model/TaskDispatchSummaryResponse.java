package com.kailei.demo.model;

public record TaskDispatchSummaryResponse(
        long total,
        long running,
        long succeeded,
        long failed
) {
}

package com.kailei.demo.model;

public record ExecutionSummary(
        int executed,
        int scheduled,
        int failed,
        int failedFinal,
        int manualReview,
        int cancelled
) {
}

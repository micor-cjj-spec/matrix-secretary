package com.kailei.demo.skill;

public record SkillRetryPolicy(
        Integer maxRetryCount,
        Integer initialBackoffSeconds,
        Integer maxBackoffSeconds,
        Integer runningTimeoutMinutes
) {

    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int DEFAULT_INITIAL_BACKOFF_SECONDS = 60;
    private static final int DEFAULT_MAX_BACKOFF_SECONDS = 30 * 60;
    private static final int DEFAULT_RUNNING_TIMEOUT_MINUTES = 10;

    public SkillRetryPolicy {
        maxRetryCount = positiveOrDefault(maxRetryCount, DEFAULT_MAX_RETRY_COUNT);
        initialBackoffSeconds = positiveOrDefault(initialBackoffSeconds, DEFAULT_INITIAL_BACKOFF_SECONDS);
        maxBackoffSeconds = positiveOrDefault(maxBackoffSeconds, DEFAULT_MAX_BACKOFF_SECONDS);
        runningTimeoutMinutes = positiveOrDefault(runningTimeoutMinutes, DEFAULT_RUNNING_TIMEOUT_MINUTES);
        if (maxBackoffSeconds < initialBackoffSeconds) {
            maxBackoffSeconds = initialBackoffSeconds;
        }
    }

    public static SkillRetryPolicy defaults() {
        return new SkillRetryPolicy(
                DEFAULT_MAX_RETRY_COUNT,
                DEFAULT_INITIAL_BACKOFF_SECONDS,
                DEFAULT_MAX_BACKOFF_SECONDS,
                DEFAULT_RUNNING_TIMEOUT_MINUTES
        );
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}

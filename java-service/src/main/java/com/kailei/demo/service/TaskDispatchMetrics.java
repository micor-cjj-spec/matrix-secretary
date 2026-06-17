package com.kailei.demo.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TaskDispatchMetrics {

    private final Counter startedCounter;
    private final Counter succeededCounter;
    private final Counter failedCounter;
    private final Counter retryStartedCounter;
    private final Counter timeoutRecoveredCounter;

    public TaskDispatchMetrics(MeterRegistry registry) {
        this.startedCounter = Counter.builder("task_dispatch_started_total")
                .description("Total number of dispatch records started")
                .register(registry);
        this.succeededCounter = Counter.builder("task_dispatch_succeeded_total")
                .description("Total number of dispatch records marked as succeeded")
                .register(registry);
        this.failedCounter = Counter.builder("task_dispatch_failed_total")
                .description("Total number of dispatch records marked as failed")
                .register(registry);
        this.retryStartedCounter = Counter.builder("task_dispatch_retry_started_total")
                .description("Total number of failed dispatch records restarted for retry")
                .register(registry);
        this.timeoutRecoveredCounter = Counter.builder("task_dispatch_timeout_recovered_total")
                .description("Total number of timed-out RUNNING dispatch records recovered")
                .register(registry);
    }

    public void incrementStarted() {
        startedCounter.increment();
    }

    public void incrementSucceeded() {
        succeededCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public void incrementRetryStarted() {
        retryStartedCounter.increment();
    }

    public void incrementTimeoutRecovered(int count) {
        if (count > 0) {
            timeoutRecoveredCounter.increment(count);
        }
    }
}

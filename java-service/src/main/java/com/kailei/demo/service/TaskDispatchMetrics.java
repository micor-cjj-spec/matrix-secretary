package com.kailei.demo.service;

import com.kailei.demo.repository.TaskDispatchRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TaskDispatchMetrics {

    private final Counter startedCounter;
    private final Counter succeededCounter;
    private final Counter failedCounter;
    private final Counter retryStartedCounter;
    private final Counter timeoutRecoveredCounter;
    private final Timer executionDurationTimer;

    public TaskDispatchMetrics(MeterRegistry registry, TaskDispatchRecordRepository dispatchRecordRepository) {
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
        this.executionDurationTimer = Timer.builder("task_dispatch_execution_duration")
                .description("Dispatch execution duration from RUNNING start to terminal update")
                .register(registry);

        Gauge.builder("task_dispatch_running_current", dispatchRecordRepository, TaskDispatchRecordRepository::countRunningRecords)
                .description("Current number of RUNNING dispatch records")
                .register(registry);
        Gauge.builder("task_dispatch_failed_current", dispatchRecordRepository, TaskDispatchRecordRepository::countFailedRecords)
                .description("Current number of FAILED dispatch records")
                .register(registry);
        Gauge.builder("task_dispatch_retry_scheduled_current", dispatchRecordRepository, TaskDispatchRecordRepository::countRetryScheduledRecords)
                .description("Current number of FAILED dispatch records that still have retry capacity")
                .register(registry);
        Gauge.builder("task_dispatch_retry_exhausted_current", dispatchRecordRepository, TaskDispatchRecordRepository::countRetryExhaustedRecords)
                .description("Current number of FAILED dispatch records that exhausted retry attempts")
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

    public void incrementFailed(int count) {
        if (count > 0) {
            failedCounter.increment(count);
        }
    }

    public void incrementRetryStarted() {
        retryStartedCounter.increment();
    }

    public void incrementTimeoutRecovered(int count) {
        if (count > 0) {
            timeoutRecoveredCounter.increment(count);
        }
    }

    public void recordExecutionDurationNanos(long durationNanos) {
        if (durationNanos >= 0) {
            executionDurationTimer.record(Duration.ofNanos(durationNanos));
        }
    }
}

package com.kailei.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai-secretary.local-scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchScheduler.class);

    private final AiTaskService aiTaskService;
    private final Long dispatchPageSize;
    private final Long dispatchLeaseSeconds;
    private final String dispatchOwner;

    public TaskDispatchScheduler(AiTaskService aiTaskService,
                                 @Value("${ai-secretary.local-scheduler.dispatch-page-size:50}") Long dispatchPageSize,
                                 @Value("${ai-secretary.local-scheduler.dispatch-lease-seconds:60}") Long dispatchLeaseSeconds,
                                 @Value("${ai-secretary.local-scheduler.dispatch-owner:local-scheduler}") String dispatchOwner) {
        this.aiTaskService = aiTaskService;
        this.dispatchPageSize = dispatchPageSize;
        this.dispatchLeaseSeconds = dispatchLeaseSeconds;
        this.dispatchOwner = dispatchOwner;
    }

    @Scheduled(fixedDelay = 10_000)
    public void dispatchDueTasks() {
        try {
            aiTaskService.dispatchDueOnceTasks(dispatchPageSize, dispatchLeaseSeconds, dispatchOwner);
        } catch (DataAccessException ex) {
            log.warn("Skip task dispatch because database is unavailable: {}", ex.getMostSpecificCause().getMessage());
        }
    }
}

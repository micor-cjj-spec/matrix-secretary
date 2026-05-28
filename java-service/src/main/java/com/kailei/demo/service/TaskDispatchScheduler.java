package com.kailei.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchScheduler.class);

    private final AiTaskService aiTaskService;

    public TaskDispatchScheduler(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @Scheduled(fixedDelay = 10_000)
    public void dispatchDueTasks() {
        try {
            aiTaskService.dispatchDueOnceTasks();
        } catch (DataAccessException ex) {
            log.warn("Skip task dispatch because database is unavailable: {}", ex.getMostSpecificCause().getMessage());
        }
    }
}

package com.kailei.demo.service;

import com.kailei.demo.entity.TaskActionExecutionEntity;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.TaskActionExecutionRepository;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.skill.GenericSkillExecutor;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final SkillCatalog skillCatalog;
    private final GenericSkillExecutor genericSkillExecutor;
    private final TaskExecutionLogRepository executionLogRepository;
    private final CronScheduleService cronScheduleService;
    private final TaskActionExecutionRepository actionExecutionRepository;

    public TaskExecutionService(SkillCatalog skillCatalog,
                                GenericSkillExecutor genericSkillExecutor,
                                TaskExecutionLogRepository executionLogRepository,
                                CronScheduleService cronScheduleService,
                                TaskActionExecutionRepository actionExecutionRepository) {
        this.skillCatalog = skillCatalog;
        this.genericSkillExecutor = genericSkillExecutor;
        this.executionLogRepository = executionLogRepository;
        this.cronScheduleService = cronScheduleService;
        this.actionExecutionRepository = actionExecutionRepository;
    }

    public TaskAction confirmAction(String planId, String userId, TaskAction action, String operatorUserId) {
        if (action.schedule() != null && action.schedule().isScheduled()) {
            TaskAction scheduledAction = action.withSchedule(cronScheduleService.ensureCronAndNextRun(action.schedule()));
            String note = "已进入调度队列: cron=" + scheduledAction.schedule().cron()
                    + ", nextRunAt=" + scheduledAction.schedule().effectiveRunAt();
            log.info("Schedule action [{}] {}", action.actionId(), note);
            TaskAction next = scheduledAction.withStatus(TaskStatus.SCHEDULED, note);
            executionLogRepository.logStateChange(planId, action, next, operatorUserId);
            return next;
        }
        return executeNow(planId, userId, action, operatorUserId);
    }

    public TaskAction confirmAction(String planId, TaskAction action, String operatorUserId) {
        return confirmAction(planId, operatorUserId, action, operatorUserId);
    }

    public TaskAction executeNow(String planId, String userId, TaskAction action, String operatorUserId) {
        SkillDefinition skill = skillCatalog.getOrUnknown(action.actionType());
        String idempotencyKey = actionExecutionRepository.idempotencyKey(action);
        Optional<TaskActionExecutionEntity> executedBefore = actionExecutionRepository.findExecutedByIdempotencyKey(idempotencyKey);
        if (executedBefore.isPresent()) {
            String note = "幂等命中，跳过重复执行: idempotencyKey=" + idempotencyKey;
            TaskAction next = action.withStatus(TaskStatus.EXECUTED, note);
            executionLogRepository.logStateChange(planId, action, next, operatorUserId);
            return next;
        }

        TaskAction next = genericSkillExecutor.execute(planId, userId, skill, action);
        actionExecutionRepository.record(planId, userId, skill, action, next, operatorUserId);
        executionLogRepository.logStateChange(planId, action, next, operatorUserId);
        return next;
    }

    public TaskAction executeNow(String planId, TaskAction action, String operatorUserId) {
        return executeNow(planId, operatorUserId, action, operatorUserId);
    }
}

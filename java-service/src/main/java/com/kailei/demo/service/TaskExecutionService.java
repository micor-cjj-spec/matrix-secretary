package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import com.kailei.demo.skill.GenericSkillExecutor;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final SkillCatalog skillCatalog;
    private final GenericSkillExecutor genericSkillExecutor;
    private final TaskExecutionLogRepository executionLogRepository;
    private final CronScheduleService cronScheduleService;
    private final TaskStateMachineService stateMachine;
    private final TaskPlanRepository taskPlanRepository;

    public TaskExecutionService(SkillCatalog skillCatalog,
                                GenericSkillExecutor genericSkillExecutor,
                                TaskExecutionLogRepository executionLogRepository,
                                CronScheduleService cronScheduleService,
                                TaskStateMachineService stateMachine,
                                TaskPlanRepository taskPlanRepository) {
        this.skillCatalog = skillCatalog;
        this.genericSkillExecutor = genericSkillExecutor;
        this.executionLogRepository = executionLogRepository;
        this.cronScheduleService = cronScheduleService;
        this.stateMachine = stateMachine;
        this.taskPlanRepository = taskPlanRepository;
    }

    public TaskAction confirmAction(String planId, String userId, TaskAction action, String operatorUserId) {
        stateMachine.requireConfirmableAction(action);
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
        stateMachine.requireExecutableAction(action);
        TaskAction running = action.withStatus(TaskStatus.RUNNING, "Task execution started");
        taskPlanRepository.markActionRunning(running, running.executionNote());
        executionLogRepository.logStateChange(planId, action, running, operatorUserId);

        try {
            SkillDefinition skill = skillCatalog.getOrUnknown(action.actionType());
            TaskAction next = genericSkillExecutor.execute(planId, userId, skill, running);
            taskPlanRepository.markActionResult(next);
            executionLogRepository.logStateChange(planId, running, next, operatorUserId);
            return next;
        } catch (RuntimeException ex) {
            log.warn("Task action [{}] execution failed unexpectedly", action.actionId(), ex);
            TaskAction failed = running.withStatus(TaskStatus.FAILED,
                    "Task execution failed: " + ex.getClass().getSimpleName());
            taskPlanRepository.markActionResult(failed);
            executionLogRepository.logStateChange(planId, running, failed, operatorUserId);
            return failed;
        }
    }

    public TaskAction executeNow(String planId, TaskAction action, String operatorUserId) {
        return executeNow(planId, operatorUserId, action, operatorUserId);
    }
}

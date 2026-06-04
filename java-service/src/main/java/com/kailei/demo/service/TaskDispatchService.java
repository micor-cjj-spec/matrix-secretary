package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.AiSessionRepository;
import com.kailei.demo.repository.TaskActionExecutionRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class TaskDispatchService {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchService.class);
    private static final String SYSTEM_OPERATOR = "system-scheduler";
    private static final int DEFAULT_DISPATCH_LIMIT = 100;
    private final String schedulerInstanceId = buildSchedulerInstanceId();

    private final TaskPlanRepository taskPlanRepository;
    private final TaskActionExecutionRepository actionExecutionRepository;
    private final TaskExecutionService executionService;
    private final CronScheduleService cronScheduleService;
    private final TaskStateMachineService stateMachineService;
    private final AiSessionRepository sessionRepository;

    public TaskDispatchService(TaskPlanRepository taskPlanRepository,
                               TaskActionExecutionRepository actionExecutionRepository,
                               TaskExecutionService executionService,
                               CronScheduleService cronScheduleService,
                               TaskStateMachineService stateMachineService,
                               AiSessionRepository sessionRepository) {
        this.taskPlanRepository = taskPlanRepository;
        this.actionExecutionRepository = actionExecutionRepository;
        this.executionService = executionService;
        this.cronScheduleService = cronScheduleService;
        this.stateMachineService = stateMachineService;
        this.sessionRepository = sessionRepository;
    }

    public void dispatchDueOnceTasks() {
        dispatchDueOnceTasks(DEFAULT_DISPATCH_LIMIT);
    }

    public void dispatchDueOnceTasks(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        int recovered = actionExecutionRepository.recoverStaleRunningExecutions(now);
        if (recovered > 0) {
            log.warn("Recovered stale RUNNING task action executions: count={}", recovered);
        }
        List<TaskPlan> retryPlans = taskPlanRepository.rescheduleRetryableFailedPlans(now, limit);
        if (!retryPlans.isEmpty()) {
            retryPlans.forEach(plan -> sessionRepository.updateAfterPlanChange(
                    plan.withStatus(stateMachineService.resolvePlanStatus(plan.tasks()), plan.tasks())
            ));
            log.info("Rescheduled retryable FAILED task plans: count={}", retryPlans.size());
        }
        taskPlanRepository.findDueScheduledPlans(now, limit).forEach(plan -> {
            List<TaskAction> nextActions = plan.tasks().stream()
                    .map(action -> dispatchIfDue(plan, action, now))
                    .toList();
            if (!nextActions.equals(plan.tasks())) {
                TaskPlan saved = taskPlanRepository.save(plan.withStatus(stateMachineService.resolvePlanStatus(nextActions), nextActions));
                sessionRepository.updateAfterPlanChange(saved);
            }
        });
    }

    private TaskAction dispatchIfDue(TaskPlan plan, TaskAction action, OffsetDateTime now) {
        if (action.status() != TaskStatus.SCHEDULED || action.schedule() == null) {
            return action;
        }
        TaskSchedule schedule = cronScheduleService.ensureCronAndNextRun(action.schedule());
        if (schedule == null || schedule.effectiveRunAt() == null) {
            return action.withStatus(TaskStatus.FAILED, "调度任务缺少可执行时间或cron表达式");
        }
        try {
            OffsetDateTime runAt = OffsetDateTime.parse(schedule.effectiveRunAt());
            if (runAt.isAfter(now)) {
                return action.withSchedule(schedule);
            }
            boolean locked = taskPlanRepository.tryLockAction(action.actionId(), schedulerInstanceId, now);
            if (!locked) {
                return action;
            }
            try {
                TaskAction executable = action.withSchedule(schedule);
                TaskAction executed = executionService.executeNow(plan.planId(), plan.userId(), executable, SYSTEM_OPERATOR);
                if (schedule.isRecurring() && executed.status() == TaskStatus.EXECUTED) {
                    TaskSchedule nextSchedule = cronScheduleService.markTriggered(schedule, now);
                    String note = "周期任务已执行，下一次触发: cron=" + nextSchedule.cron()
                            + ", nextRunAt=" + nextSchedule.nextRunAt();
                    return executed.withSchedule(nextSchedule).withStatus(TaskStatus.SCHEDULED, note);
                }
                return executed;
            } finally {
                taskPlanRepository.releaseActionLock(action.actionId(), schedulerInstanceId);
            }
        } catch (DateTimeParseException ex) {
            return action.withStatus(TaskStatus.FAILED, "时间格式无法解析: " + schedule.effectiveRunAt());
        }
    }

    private String buildSchedulerInstanceId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return "scheduler-" + runtimeName + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.AiSessionRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class TaskDispatchService {

    private static final String SYSTEM_OPERATOR = "system-scheduler";
    private static final int DEFAULT_DISPATCH_LIMIT = 100;

    private final TaskPlanRepository taskPlanRepository;
    private final TaskExecutionService executionService;
    private final CronScheduleService cronScheduleService;
    private final TaskStateMachineService stateMachineService;
    private final AiSessionRepository sessionRepository;

    public TaskDispatchService(TaskPlanRepository taskPlanRepository,
                               TaskExecutionService executionService,
                               CronScheduleService cronScheduleService,
                               TaskStateMachineService stateMachineService,
                               AiSessionRepository sessionRepository) {
        this.taskPlanRepository = taskPlanRepository;
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
            TaskAction executable = action.withSchedule(schedule);
            TaskAction executed = executionService.executeNow(plan.planId(), plan.userId(), executable, SYSTEM_OPERATOR);
            if (schedule.isRecurring() && executed.status() == TaskStatus.EXECUTED) {
                TaskSchedule nextSchedule = cronScheduleService.markTriggered(schedule, now);
                String note = "周期任务已执行，下一次触发: cron=" + nextSchedule.cron()
                        + ", nextRunAt=" + nextSchedule.nextRunAt();
                return executed.withSchedule(nextSchedule).withStatus(TaskStatus.SCHEDULED, note);
            }
            return executed;
        } catch (DateTimeParseException ex) {
            return action.withStatus(TaskStatus.FAILED, "时间格式无法解析: " + schedule.effectiveRunAt());
        }
    }
}

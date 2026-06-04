package com.kailei.demo.service;

import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.AiSessionRepository;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.repository.TaskPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskCommandServiceTest {

    private TaskPlanRepository taskPlanRepository;
    private TaskExecutionService executionService;
    private CronScheduleService cronScheduleService;
    private TaskExecutionLogRepository executionLogRepository;
    private AiSessionRepository sessionRepository;
    private TaskQueryService taskQueryService;
    private TaskCommandService commandService;

    @BeforeEach
    void setUp() {
        taskPlanRepository = mock(TaskPlanRepository.class);
        executionService = mock(TaskExecutionService.class);
        cronScheduleService = mock(CronScheduleService.class);
        executionLogRepository = mock(TaskExecutionLogRepository.class);
        sessionRepository = mock(AiSessionRepository.class);
        taskQueryService = mock(TaskQueryService.class);
        commandService = new TaskCommandService(
                taskPlanRepository,
                executionService,
                cronScheduleService,
                executionLogRepository,
                sessionRepository,
                new TaskStateMachineService(),
                taskQueryService
        );
    }

    @Test
    void editActionUpdatesEditableFieldsAndLogsStateChange() {
        TaskPlan plan = plan(
                TaskStatus.WAITING_CONFIRM,
                action("act-1", TaskStatus.WAITING_CONFIRM, "原标题", "原内容")
        );
        when(taskQueryService.get("plan-1")).thenReturn(plan);
        when(taskPlanRepository.save(any(TaskPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EditTaskActionRequest request = new EditTaskActionRequest(
                "demo-user",
                "新标题",
                "新内容",
                null,
                null,
                Map.of("k", "v"),
                "P1",
                true
        );

        TaskPlan saved = commandService.editAction("plan-1", "act-1", request);

        TaskAction edited = saved.tasks().get(0);
        assertEquals("新标题", edited.title());
        assertEquals("新内容", edited.content());
        assertEquals("P1", edited.priority());
        assertEquals(true, edited.requiresConfirmation());
        assertEquals(TaskStatus.WAITING_CONFIRM, edited.status());
        assertEquals("用户已编辑任务参数，等待确认", edited.executionNote());
        verify(executionLogRepository).logStateChange(eq("plan-1"), eq(plan.tasks().get(0)), eq(edited), eq("demo-user"));
        verify(sessionRepository).updateAfterPlanChange(saved);
    }

    @Test
    void editActionRejectsOperatorFromAnotherUser() {
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action("act-1", TaskStatus.WAITING_CONFIRM));
        when(taskQueryService.get("plan-1")).thenReturn(plan);

        EditTaskActionRequest request = new EditTaskActionRequest(
                "other-user",
                "新标题",
                null,
                null,
                null,
                null,
                null,
                null
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> commandService.editAction("plan-1", "act-1", request)
        );
        assertEquals("任务计划不存在或无权操作: plan-1", ex.getMessage());
        verify(taskPlanRepository, never()).save(any(TaskPlan.class));
    }

    @Test
    void cancelRejectsPlanWithExecutedAction() {
        TaskPlan plan = plan(
                TaskStatus.SCHEDULED,
                action("act-1", TaskStatus.EXECUTED),
                action("act-2", TaskStatus.SCHEDULED)
        );
        when(taskQueryService.get("plan-1")).thenReturn(plan);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> commandService.cancel("plan-1", "demo-user", "用户取消")
        );
        assertEquals("任务已存在已执行动作，不能整体取消: plan-1", ex.getMessage());
        verify(taskPlanRepository, never()).save(any(TaskPlan.class));
    }

    @Test
    void cancelMarksAllNonCancelledActionsAsCancelled() {
        TaskPlan plan = plan(
                TaskStatus.SCHEDULED,
                action("act-1", TaskStatus.SCHEDULED),
                action("act-2", TaskStatus.WAITING_CONFIRM)
        );
        when(taskQueryService.get("plan-1")).thenReturn(plan);
        when(taskPlanRepository.save(any(TaskPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmTaskResponse response = commandService.cancel("plan-1", "demo-user", "不需要了");

        assertEquals(TaskStatus.CANCELLED, response.status());
        assertEquals(TaskStatus.CANCELLED, response.plan().tasks().get(0).status());
        assertEquals(TaskStatus.CANCELLED, response.plan().tasks().get(1).status());
        verify(executionLogRepository).logStateChange(eq("plan-1"), eq(plan.tasks().get(0)), any(TaskAction.class), eq("demo-user"));
        verify(executionLogRepository).logStateChange(eq("plan-1"), eq(plan.tasks().get(1)), any(TaskAction.class), eq("demo-user"));
        verify(sessionRepository).updateAfterPlanChange(response.plan());
    }

    @Test
    void retryRejectsNonFailedAction() {
        TaskPlan plan = plan(TaskStatus.SCHEDULED, action("act-1", TaskStatus.SCHEDULED));
        when(taskQueryService.get("plan-1")).thenReturn(plan);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> commandService.retryAction("plan-1", "act-1", "demo-user")
        );
        assertEquals("只有 FAILED 状态的动作允许重试: act-1", ex.getMessage());
        verify(executionService, never()).executeNow(any(), any(), any(), any());
        verify(taskPlanRepository, never()).save(any(TaskPlan.class));
    }

    @Test
    void retryExecutesFailedActionAndPersistsResolvedPlanStatus() {
        TaskAction failed = action("act-1", TaskStatus.FAILED);
        TaskPlan plan = plan(TaskStatus.FAILED, failed);
        TaskAction executed = failed.withStatus(TaskStatus.EXECUTED, "重试成功");
        when(taskQueryService.get("plan-1")).thenReturn(plan);
        when(executionService.executeNow("plan-1", "demo-user", failed, "demo-user")).thenReturn(executed);
        when(taskPlanRepository.save(any(TaskPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmTaskResponse response = commandService.retryAction("plan-1", "act-1", "demo-user");

        assertEquals(TaskStatus.EXECUTED, response.status());
        assertEquals(1, response.executionSummary().executed());
        assertEquals("重试成功", response.plan().tasks().get(0).executionNote());
        verify(sessionRepository).updateAfterPlanChange(response.plan());
    }

    @Test
    void confirmExecutesWaitingActionsAndPersistsResolvedPlanStatus() {
        TaskAction action = action("act-1", TaskStatus.WAITING_CONFIRM);
        TaskPlan plan = plan(TaskStatus.WAITING_CONFIRM, action);
        TaskAction scheduled = action.withStatus(TaskStatus.SCHEDULED, "已进入调度队列");
        when(taskQueryService.get("plan-1")).thenReturn(plan);
        when(executionService.confirmAction("plan-1", "demo-user", action, "demo-user")).thenReturn(scheduled);
        when(taskPlanRepository.save(any(TaskPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmTaskResponse response = commandService.confirm("plan-1", "demo-user");

        assertEquals(TaskStatus.SCHEDULED, response.status());
        assertEquals(1, response.executionSummary().scheduled());
        verify(sessionRepository).updateAfterPlanChange(response.plan());
    }

    @Test
    void confirmReturnsExistingPlanWhenAlreadyNotWaitingConfirm() {
        TaskPlan plan = plan(TaskStatus.SCHEDULED, action("act-1", TaskStatus.SCHEDULED));
        when(taskQueryService.get("plan-1")).thenReturn(plan);

        ConfirmTaskResponse response = commandService.confirm("plan-1", "demo-user");

        assertEquals(TaskStatus.SCHEDULED, response.status());
        assertEquals(plan, response.plan());
        verify(executionService, never()).confirmAction(any(), any(), any(), any());
        verify(taskPlanRepository, never()).save(any(TaskPlan.class));
    }

    private TaskPlan plan(TaskStatus status, TaskAction... actions) {
        return new TaskPlan(
                "plan-1",
                "trace-1",
                "session-1",
                "source text",
                "demo-user",
                status,
                List.of(actions),
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private TaskAction action(String actionId, TaskStatus status) {
        return action(actionId, status, "提醒事项", "该喝水了");
    }

    private TaskAction action(String actionId, TaskStatus status, String title, String content) {
        return new TaskAction(
                actionId,
                "reminder",
                "reminder",
                title,
                content,
                null,
                null,
                Map.of(),
                "P2",
                "LOW",
                0.9,
                false,
                "提醒我喝水",
                "command test fixture",
                status,
                "test note"
        );
    }
}

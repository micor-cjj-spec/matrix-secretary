package com.kailei.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kailei.demo.config.GlobalExceptionHandler;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.ExecutionSummary;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskSchedule;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.model.TaskTarget;
import com.kailei.demo.repository.TaskExecutionLogRepository;
import com.kailei.demo.service.AiTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiTaskControllerTest {

    private AiTaskService aiTaskService;
    private TaskExecutionLogRepository executionLogRepository;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        aiTaskService = mock(AiTaskService.class);
        executionLogRepository = mock(TaskExecutionLogRepository.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiTaskController(aiTaskService, executionLogRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void previewShouldDelegateToServiceAndReturnTaskPlan() throws Exception {
        when(aiTaskService.preview(any(PreviewTaskRequest.class))).thenReturn(plan(TaskStatus.WAITING_CONFIRM));

        mockMvc.perform(post("/api/ai-task/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "明天下午三点提醒我给李雷确认合同盖章",
                                "timezone", "Asia/Shanghai",
                                "userId", "demo-user"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value("plan-test"))
                .andExpect(jsonPath("$.status").value("WAITING_CONFIRM"));

        ArgumentCaptor<PreviewTaskRequest> captor = ArgumentCaptor.forClass(PreviewTaskRequest.class);
        verify(aiTaskService).preview(captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("明天下午三点提醒我给李雷确认合同盖章");
        assertThat(captor.getValue().effectiveTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(captor.getValue().userId()).isEqualTo("demo-user");
    }

    @Test
    void blankPreviewTextShouldReturnValidationFailed() throws Exception {
        mockMvc.perform(post("/api/ai-task/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", " ",
                                "timezone", "Asia/Shanghai",
                                "userId", "demo-user"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.text").exists());
    }

    @Test
    void confirmShouldPassOperatorUserIdToService() throws Exception {
        when(aiTaskService.confirm("plan-test", "demo-user"))
                .thenReturn(new ConfirmTaskResponse(
                        "plan-test",
                        TaskStatus.EXECUTED,
                        new ExecutionSummary(1, 0, 0),
                        plan(TaskStatus.EXECUTED)
                ));

        mockMvc.perform(post("/api/ai-task/plan-test/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorUserId", "demo-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value("plan-test"))
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.executionSummary.executed").value(1));

        verify(aiTaskService).confirm("plan-test", "demo-user");
    }

    @Test
    void logsShouldCheckPlanAccessBeforeReturningLogs() throws Exception {
        when(aiTaskService.get("plan-test", "demo-user")).thenReturn(plan(TaskStatus.SCHEDULED));
        when(executionLogRepository.findByPlanId("plan-test")).thenReturn(List.of());

        mockMvc.perform(get("/api/ai-task/plan-test/logs")
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(aiTaskService).get("plan-test", "demo-user");
        verify(executionLogRepository).findByPlanId("plan-test");
    }

    private static TaskPlan plan(TaskStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-22T10:00:00+08:00");
        return new TaskPlan(
                "plan-test",
                "trace-test",
                "session-test",
                "明天下午三点提醒我给李雷确认合同盖章",
                "demo-user",
                status,
                List.of(action(status)),
                List.of(),
                now,
                now
        );
    }

    private static TaskAction action(TaskStatus status) {
        return new TaskAction(
                "action-test",
                "reminder",
                "reminder",
                "提醒事项",
                "确认合同盖章",
                new TaskTarget("user", "李雷", null),
                new TaskSchedule("none", null, null, null, "Asia/Shanghai", null, null, 0),
                Map.of(),
                "normal",
                "LOW",
                0.9,
                false,
                "明天下午三点提醒我给李雷确认合同盖章",
                "测试解析备注",
                status,
                "测试执行说明"
        );
    }
}

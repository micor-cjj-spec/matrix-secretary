package com.kailei.demo.controller;

import com.kailei.demo.model.ConfirmTaskRequest;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.service.AiTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai-task")
public class AiTaskController {

    private final AiTaskService aiTaskService;

    public AiTaskController(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @PostMapping("/preview")
    public TaskPlan preview(@Valid @RequestBody PreviewTaskRequest request) {
        return aiTaskService.preview(request);
    }

    @PostMapping("/{planId}/confirm")
    public ConfirmTaskResponse confirm(@PathVariable String planId,
                                       @RequestBody(required = false) ConfirmTaskRequest request) {
        return aiTaskService.confirm(planId);
    }

    @GetMapping("/{planId}")
    public TaskPlan get(@PathVariable String planId) {
        return aiTaskService.get(planId);
    }

    @GetMapping
    public List<TaskPlan> list() {
        return aiTaskService.list();
    }
}

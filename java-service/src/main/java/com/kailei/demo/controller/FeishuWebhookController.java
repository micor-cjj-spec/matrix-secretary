package com.kailei.demo.controller;

import com.kailei.demo.channel.feishu.FeishuChannelAdapter;
import com.kailei.demo.channel.model.ChannelIncomingMessage;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.service.AiTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/channels/feishu")
public class FeishuWebhookController {

    private static final Logger log = LoggerFactory.getLogger(FeishuWebhookController.class);

    private final FeishuChannelAdapter feishuChannelAdapter;
    private final AiTaskService aiTaskService;

    public FeishuWebhookController(FeishuChannelAdapter feishuChannelAdapter, AiTaskService aiTaskService) {
        this.feishuChannelAdapter = feishuChannelAdapter;
        this.aiTaskService = aiTaskService;
    }

    @PostMapping("/events")
    public Object receive(@RequestBody Map<String, Object> rawEvent) {
        if ("url_verification".equals(string(rawEvent.get("type")))) {
            return Map.of("challenge", string(rawEvent.get("challenge")));
        }
        if (!feishuChannelAdapter.verifyToken(rawEvent)) {
            throw new IllegalArgumentException("飞书事件 token 校验失败");
        }
        return feishuChannelAdapter.parseIncoming(rawEvent)
                .map(this::handleIncomingMessage)
                .orElseGet(() -> Map.of("status", "ignored"));
    }

    private Object handleIncomingMessage(ChannelIncomingMessage message) {
        log.info("Receive Feishu message: sender={}, conversation={}, text={}",
                message.senderId(), message.conversationId(), message.text());
        PreviewTaskRequest request = new PreviewTaskRequest(
                message.text(),
                "Asia/Shanghai",
                message.secretaryUserId(),
                message.secretarySessionId()
        );
        TaskPlan preview = aiTaskService.preview(request);
        TaskPlan enriched = enrichFeishuContext(preview, message);
        if (!canAutoConfirm(enriched.tasks())) {
            return Map.of(
                    "status", "waiting_confirm",
                    "planId", enriched.planId(),
                    "message", "任务已生成，但包含需要确认的动作"
            );
        }
        ConfirmTaskResponse confirmed = aiTaskService.confirm(enriched.planId(), enriched.userId());
        return Map.of(
                "status", "ok",
                "planId", confirmed.planId(),
                "planStatus", confirmed.status()
        );
    }

    private TaskPlan enrichFeishuContext(TaskPlan plan, ChannelIncomingMessage message) {
        TaskPlan current = plan;
        for (TaskAction action : plan.tasks()) {
            Map<String, Object> args = new LinkedHashMap<>(action.args());
            args.put("platform", "feishu");
            args.put("tenantId", message.tenantId());
            args.put("conversationId", message.conversationId());
            args.put("receiverId", message.senderId());
            args.put("replyToMessageId", message.messageId());
            EditTaskActionRequest editRequest = new EditTaskActionRequest(
                    plan.userId(),
                    null,
                    normalizeReminderContent(action.content()),
                    null,
                    null,
                    args,
                    null,
                    false
            );
            current = aiTaskService.editAction(current.planId(), action.actionId(), editRequest);
        }
        return current;
    }

    private boolean canAutoConfirm(List<TaskAction> actions) {
        if (actions.isEmpty()) {
            return false;
        }
        return actions.stream().allMatch(action ->
                !Boolean.TRUE.equals(action.requiresConfirmation())
                        && (action.status() == TaskStatus.WAITING_CONFIRM)
                        && ("reminder".equalsIgnoreCase(action.actionType())
                        || "create_todo".equalsIgnoreCase(action.actionType()))
        );
    }

    private String normalizeReminderContent(String content) {
        if (content == null || content.isBlank()) {
            return "该处理提醒事项了";
        }
        String normalized = content.trim();
        if (normalized.startsWith("喝水")) {
            return "该喝水了";
        }
        return normalized;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

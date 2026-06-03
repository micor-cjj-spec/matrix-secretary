package com.kailei.demo.channel.core;

import com.kailei.demo.channel.model.ChannelIncomingMessage;
import com.kailei.demo.channel.model.ChannelOutgoingMessage;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.service.AiTaskService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChannelTaskFacade {

    private static final Pattern CONFIRM_PATTERN = Pattern.compile("^(确认|confirm)\\s+([A-Za-z0-9_-]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCEL_PATTERN = Pattern.compile("^(取消|cancel)\\s+([A-Za-z0-9_-]+)$", Pattern.CASE_INSENSITIVE);

    private final AiTaskService aiTaskService;
    private final ChannelAdapterRegistry adapterRegistry;

    public ChannelTaskFacade(AiTaskService aiTaskService, ChannelAdapterRegistry adapterRegistry) {
        this.aiTaskService = aiTaskService;
        this.adapterRegistry = adapterRegistry;
    }

    public void handleIncoming(ChannelIncomingMessage incoming) {
        if (incoming == null || incoming.text() == null || incoming.text().isBlank()) {
            return;
        }
        ChannelAdapter adapter = adapterRegistry.find(incoming.platform())
                .orElseThrow(() -> new IllegalArgumentException("未注册渠道适配器: " + incoming.platform()));
        String text = incoming.text().trim();

        Optional<String> confirmPlanId = matchPlanId(CONFIRM_PATTERN, text);
        if (confirmPlanId.isPresent()) {
            ConfirmTaskResponse response = aiTaskService.confirm(confirmPlanId.get(), incoming.secretaryUserId());
            adapter.sendText(reply(incoming, "已确认任务计划: " + response.planId() + "，当前状态: " + response.status()));
            return;
        }

        Optional<String> cancelPlanId = matchPlanId(CANCEL_PATTERN, text);
        if (cancelPlanId.isPresent()) {
            ConfirmTaskResponse response = aiTaskService.cancel(cancelPlanId.get(), incoming.secretaryUserId(), "渠道用户取消");
            adapter.sendText(reply(incoming, "已取消任务计划: " + response.planId() + "，当前状态: " + response.status()));
            return;
        }

        TaskPlan plan = aiTaskService.preview(new PreviewTaskRequest(
                text,
                "Asia/Shanghai",
                incoming.secretaryUserId(),
                incoming.secretarySessionId()
        ));
        adapter.sendText(reply(incoming, renderPreview(plan)));
    }

    private Optional<String> matchPlanId(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(2)) : Optional.empty();
    }

    private ChannelOutgoingMessage reply(ChannelIncomingMessage incoming, String content) {
        return new ChannelOutgoingMessage(
                incoming.platform(),
                incoming.tenantId(),
                incoming.conversationId(),
                null,
                incoming.messageId(),
                "text",
                content,
                incoming.raw()
        );
    }

    private String renderPreview(TaskPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("AI秘书已生成任务预览\n");
        builder.append("planId: ").append(plan.planId()).append("\n");
        builder.append("状态: ").append(plan.status()).append("\n\n");
        int index = 1;
        for (TaskAction action : plan.tasks()) {
            builder.append(index++).append(". ").append(nullToDash(action.title())).append("\n")
                    .append("   类型: ").append(nullToDash(action.actionType())).append("\n")
                    .append("   内容: ").append(nullToDash(action.content())).append("\n")
                    .append("   对象: ").append(action.target() == null ? "-" : nullToDash(action.target().name())).append("\n")
                    .append("   时间: ").append(action.schedule() == null ? "-" : nullToDash(action.schedule().effectiveRunAt())).append("\n")
                    .append("   风险: ").append(nullToDash(action.riskLevel())).append("，需要确认: ").append(action.requiresConfirmation()).append("\n");
        }
        if (!plan.warnings().isEmpty()) {
            builder.append("\n警告:\n");
            plan.warnings().forEach(warning -> builder.append("- ").append(warning).append("\n"));
        }
        builder.append("\n回复：确认 ").append(plan.planId()).append(" 执行；回复：取消 ").append(plan.planId()).append(" 取消。");
        return builder.toString();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

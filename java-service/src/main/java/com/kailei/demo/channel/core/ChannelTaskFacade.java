package com.kailei.demo.channel.core;

import com.kailei.demo.channel.model.ChannelIncomingMessage;
import com.kailei.demo.channel.model.ChannelOutgoingMessage;
import com.kailei.demo.model.ConfirmTaskResponse;
import com.kailei.demo.model.EditTaskActionRequest;
import com.kailei.demo.model.PreviewTaskRequest;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskPlan;
import com.kailei.demo.service.AiTaskService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
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
            adapter.sendText(reply(incoming, "已确认任务计划 " + response.planId() + "，当前状态：" + response.status()));
            return;
        }

        Optional<String> cancelPlanId = matchPlanId(CANCEL_PATTERN, text);
        if (cancelPlanId.isPresent()) {
            ConfirmTaskResponse response = aiTaskService.cancel(confirmPlanId.orElse(cancelPlanId.get()), incoming.secretaryUserId(), "渠道用户取消");
            adapter.sendText(reply(incoming, "已取消任务计划 " + response.planId() + "，当前状态：" + response.status()));
            return;
        }

        TaskPlan plan = aiTaskService.preview(new PreviewTaskRequest(
                text,
                "Asia/Shanghai",
                incoming.secretaryUserId(),
                incoming.secretarySessionId()
        ));
        plan = attachChannelContext(plan, incoming);
        if (canAutoConfirmReminder(plan)) {
            ConfirmTaskResponse response = aiTaskService.confirm(plan.planId(), plan.userId());
            adapter.sendText(reply(incoming, "已安排提醒：" + reminderSummary(response.plan()) + "\nplanId: " + response.planId()));
            return;
        }
        adapter.sendText(reply(incoming, renderPreview(plan)));
    }

    private TaskPlan attachChannelContext(TaskPlan plan, ChannelIncomingMessage incoming) {
        TaskPlan current = plan;
        for (TaskAction action : plan.tasks()) {
            Map<String, Object> args = new LinkedHashMap<>(action.args());
            args.putIfAbsent("platform", incoming.platform().name());
            args.putIfAbsent("tenantId", incoming.tenantId());
            args.putIfAbsent("conversationId", incoming.conversationId());
            args.putIfAbsent("receiverId", incoming.senderId());
            args.putIfAbsent("replyToMessageId", incoming.messageId());
            current = aiTaskService.editAction(
                    current.planId(),
                    action.actionId(),
                    new EditTaskActionRequest(incoming.secretaryUserId(), null, normalizeReminderContent(action), null, null, args, null, false)
            );
        }
        return current;
    }

    private boolean canAutoConfirmReminder(TaskPlan plan) {
        if (plan.tasks().isEmpty()) {
            return false;
        }
        return plan.tasks().stream().allMatch(action ->
                "reminder".equalsIgnoreCase(action.actionType())
                        && action.schedule() != null
                        && action.schedule().isScheduled()
                        && !"HIGH".equalsIgnoreCase(action.riskLevel())
        );
    }

    private String reminderSummary(TaskPlan plan) {
        return plan.tasks().stream()
                .findFirst()
                .map(action -> {
                    String content = action.content() == null || action.content().isBlank() ? "提醒事项" : action.content();
                    String time = action.schedule() == null ? null : action.schedule().effectiveRunAt();
                    return time == null || time.isBlank() ? content : content + "（" + time + "）";
                })
                .orElse("提醒事项");
    }

    private String normalizeReminderContent(TaskAction action) {
        if (!"reminder".equalsIgnoreCase(action.actionType())) {
            return null;
        }
        String content = action.content();
        if (content == null || content.isBlank()) {
            return "该处理提醒事项了";
        }
        String normalized = content.trim();
        if ("喝水".equals(normalized) || normalized.startsWith("喝水")) {
            return "该喝水了";
        }
        return normalized;
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

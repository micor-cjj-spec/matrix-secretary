package com.kailei.demo.channel.core;

import com.kailei.demo.channel.model.ChannelOutgoingMessage;
import com.kailei.demo.channel.model.ChannelPlatform;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import org.springframework.stereotype.Component;

@Component
public class ChannelMessageExecutor {

    private final ChannelAdapterRegistry adapterRegistry;

    public ChannelMessageExecutor(ChannelAdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    public TaskAction send(String planId, String userId, TaskAction action) {
        ChannelPlatform platform = resolvePlatform(userId, action);
        ChannelAdapter adapter = adapterRegistry.find(platform)
                .orElseThrow(() -> new IllegalArgumentException("未注册渠道消息发送器: " + platform));
        ChannelOutgoingMessage outgoing = new ChannelOutgoingMessage(
                platform,
                stringArg(action, "tenantId"),
                stringArg(action, "conversationId"),
                stringArg(action, "receiverId"),
                stringArg(action, "replyToMessageId"),
                "text",
                action.content(),
                action.args()
        );
        adapter.sendText(outgoing);
        return action.withStatus(TaskStatus.EXECUTED, "已发送渠道消息: " + platform + ", planId=" + planId);
    }

    private ChannelPlatform resolvePlatform(String userId, TaskAction action) {
        String platform = stringArg(action, "platform");
        if (platform != null && !platform.isBlank()) {
            return ChannelPlatform.from(platform);
        }
        if (userId != null && userId.startsWith("feishu:")) {
            return ChannelPlatform.FEISHU;
        }
        return ChannelPlatform.WEB;
    }

    private String stringArg(TaskAction action, String key) {
        Object value = action.args().get(key);
        return value == null ? null : String.valueOf(value);
    }
}

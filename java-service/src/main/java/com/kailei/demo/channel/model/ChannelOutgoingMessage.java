package com.kailei.demo.channel.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChannelOutgoingMessage(
        ChannelPlatform platform,
        String tenantId,
        String conversationId,
        String receiverId,
        String replyToMessageId,
        String messageType,
        String content,
        Map<String, Object> extra
) {
    public ChannelOutgoingMessage {
        messageType = messageType == null || messageType.isBlank() ? "text" : messageType;
        extra = extra == null ? Map.of() : new LinkedHashMap<>(extra);
    }
}

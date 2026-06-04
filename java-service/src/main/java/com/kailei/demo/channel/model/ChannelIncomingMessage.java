package com.kailei.demo.channel.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChannelIncomingMessage(
        ChannelPlatform platform,
        String tenantId,
        String conversationId,
        String messageId,
        String senderId,
        String senderName,
        String text,
        boolean mentionedBot,
        Map<String, Object> raw
) {
    public ChannelIncomingMessage {
        raw = raw == null ? Map.of() : new LinkedHashMap<>(raw);
    }

    public String secretaryUserId() {
        return platform.name().toLowerCase() + ":" + senderId;
    }

    public String secretarySessionId() {
        String tenant = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        String conversation = conversationId == null || conversationId.isBlank() ? senderId : conversationId;
        return platform.name().toLowerCase() + ":" + tenant + ":" + conversation;
    }
}

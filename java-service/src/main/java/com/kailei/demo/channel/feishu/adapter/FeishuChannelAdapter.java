package com.kailei.demo.channel.feishu.adapter;

import com.kailei.demo.channel.core.ChannelAdapter;
import com.kailei.demo.channel.feishu.client.FeishuMessageClient;
import com.kailei.demo.channel.model.ChannelIncomingMessage;
import com.kailei.demo.channel.model.ChannelOutgoingMessage;
import com.kailei.demo.channel.model.ChannelPlatform;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class FeishuChannelAdapter implements ChannelAdapter {

    private final FeishuMessageClient messageClient;
    private final ObjectMapper objectMapper;

    public FeishuChannelAdapter(FeishuMessageClient messageClient, ObjectMapper objectMapper) {
        this.messageClient = messageClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChannelPlatform platform() {
        return ChannelPlatform.FEISHU;
    }

    @Override
    public Optional<ChannelIncomingMessage> parseIncoming(Map<String, Object> rawEvent) {
        Map<String, Object> event = map(rawEvent.get("event"));
        if (event.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> message = map(event.get("message"));
        Map<String, Object> sender = map(event.get("sender"));
        String messageType = string(message.get("message_type"));
        if (messageType != null && !"text".equalsIgnoreCase(messageType)) {
            return Optional.empty();
        }

        String text = extractText(message.get("content"));
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> senderId = map(sender.get("sender_id"));
        Map<String, Object> header = map(rawEvent.get("header"));
        String tenantId = firstNonBlank(string(header.get("tenant_key")), string(rawEvent.get("tenant_key")));
        String conversationId = string(message.get("chat_id"));
        String senderOpenId = firstNonBlank(string(senderId.get("open_id")), string(sender.get("open_id")));

        return Optional.of(new ChannelIncomingMessage(
                ChannelPlatform.FEISHU,
                tenantId,
                conversationId,
                string(message.get("message_id")),
                senderOpenId,
                string(sender.get("sender_name")),
                text.trim(),
                message.containsKey("mentions"),
                new LinkedHashMap<>(rawEvent)
        ));
    }

    @Override
    public void sendText(ChannelOutgoingMessage message) {
        if (message.conversationId() != null && !message.conversationId().isBlank()) {
            messageClient.sendTextToChat(message.conversationId(), message.content());
            return;
        }
        if (message.receiverId() != null && !message.receiverId().isBlank()) {
            messageClient.sendTextToOpenId(message.receiverId(), message.content());
            return;
        }
        throw new IllegalArgumentException("飞书出站消息缺少 conversationId 或 receiverId");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> out = new LinkedHashMap<>();
            raw.forEach((key, val) -> out.put(String.valueOf(key), val));
            return out;
        }
        return Map.of();
    }

    private String extractText(Object contentValue) {
        if (contentValue == null) {
            return null;
        }
        if (contentValue instanceof Map<?, ?> raw) {
            return string(raw.get("text"));
        }
        String content = String.valueOf(contentValue);
        try {
            Map<String, Object> contentMap = objectMapper.readValue(content, new TypeReference<>() {});
            return string(contentMap.get("text"));
        } catch (Exception ignored) {
            return content;
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

package com.kailei.demo.channel.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.channel.core.ChannelAdapter;
import com.kailei.demo.channel.model.ChannelIncomingMessage;
import com.kailei.demo.channel.model.ChannelOutgoingMessage;
import com.kailei.demo.channel.model.ChannelPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class FeishuChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(FeishuChannelAdapter.class);

    private final FeishuProperties properties;
    private final ObjectMapper objectMapper;

    public FeishuChannelAdapter(FeishuProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChannelPlatform platform() {
        return ChannelPlatform.FEISHU;
    }

    @Override
    public Optional<ChannelIncomingMessage> parseIncoming(Map<String, Object> rawEvent) {
        if (rawEvent == null || rawEvent.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> event = map(rawEvent.get("event"));
        Map<String, Object> message = map(event.get("message"));
        Map<String, Object> sender = map(event.get("sender"));
        String messageType = string(message.get("message_type"));
        if (messageType != null && !"text".equalsIgnoreCase(messageType)) {
            log.info("Skip non-text Feishu message type={}", messageType);
            return Optional.empty();
        }
        String text = extractText(message);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String tenantId = firstNonBlank(string(rawEvent.get("tenant_key")), string(event.get("tenant_key")));
        String conversationId = firstNonBlank(string(message.get("chat_id")), string(message.get("open_chat_id")));
        String messageId = string(message.get("message_id"));
        String senderId = extractSenderId(sender);
        String senderName = extractSenderName(sender);
        return Optional.of(new ChannelIncomingMessage(
                ChannelPlatform.FEISHU,
                tenantId,
                conversationId,
                messageId,
                senderId,
                senderName,
                cleanBotMention(text),
                text.contains("<at"),
                rawEvent
        ));
    }

    @Override
    public void sendText(ChannelOutgoingMessage message) {
        if (!properties.mockSendEnabled()) {
            throw new IllegalStateException("当前提交先支持飞书 mock-send 闭环，请设置 FEISHU_MOCK_SEND_ENABLED=true 测试调度链路");
        }
        log.info("Mock send Feishu text: conversationId={}, receiverId={}, content={}",
                message.conversationId(), message.receiverId(), message.content());
    }

    public boolean verifyToken(Map<String, Object> rawEvent) {
        String expected = properties.verificationToken();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(string(rawEvent.get("token")));
    }

    private String extractText(Map<String, Object> message) {
        Object contentRaw = message.get("content");
        if (contentRaw == null) {
            return null;
        }
        if (contentRaw instanceof String contentText) {
            try {
                Map<?, ?> content = objectMapper.readValue(contentText, Map.class);
                return string(content.get("text"));
            } catch (JsonProcessingException ignored) {
                return contentText;
            }
        }
        if (contentRaw instanceof Map<?, ?> content) {
            return string(content.get("text"));
        }
        return string(contentRaw);
    }

    private String extractSenderId(Map<String, Object> sender) {
        Map<String, Object> senderId = map(sender.get("sender_id"));
        return firstNonBlank(
                string(senderId.get("open_id")),
                string(senderId.get("user_id")),
                string(sender.get("open_id")),
                string(sender.get("user_id"))
        );
    }

    private String extractSenderName(Map<String, Object> sender) {
        Map<String, Object> senderName = map(sender.get("sender_name"));
        return firstNonBlank(string(senderName.get("name")), string(senderName.get("en_name")), string(sender.get("name")));
    }

    private String cleanBotMention(String text) {
        return text.replaceAll("<at[^>]*>.*?</at>", "").trim();
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

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

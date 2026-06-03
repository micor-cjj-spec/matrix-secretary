package com.kailei.demo.channel.feishu.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.channel.feishu.config.FeishuProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class FeishuMessageClient {

    private final FeishuProperties properties;
    private final FeishuTokenService tokenService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FeishuMessageClient(FeishuProperties properties,
                               FeishuTokenService tokenService,
                               RestClient restClient,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String sendTextToChat(String chatId, String text) {
        if (!properties.isEnabled()) {
            return "飞书未启用，跳过发送";
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("发送飞书消息失败: chatId 为空");
        }
        if (properties.isMockSendEnabled()) {
            return "飞书模拟发送成功: chat_id=" + chatId;
        }
        return send("chat_id", chatId, text);
    }

    public String sendTextToOpenId(String openId, String text) {
        if (!properties.isEnabled()) {
            return "飞书未启用，跳过发送";
        }
        if (openId == null || openId.isBlank()) {
            throw new IllegalArgumentException("发送飞书消息失败: openId 为空");
        }
        if (properties.isMockSendEnabled()) {
            return "飞书模拟发送成功: open_id=" + openId;
        }
        return send("open_id", openId, text);
    }

    private String send(String receiveIdType, String receiveId, String text) {
        String content = toJson(Map.of("text", text == null ? "" : text));
        MessageResponse response = restClient.post()
                .uri(properties.getBaseUrl() + "/open-apis/im/v1/messages?receive_id_type=" + receiveIdType)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.tenantAccessToken())
                .body(Map.of(
                        "receive_id", receiveId,
                        "msg_type", "text",
                        "content", content
                ))
                .retrieve()
                .body(MessageResponse.class);
        if (response == null || response.code() != 0) {
            throw new IllegalStateException("发送飞书消息失败: " + (response == null ? "empty response" : response.msg()));
        }
        return response.data() == null ? "发送成功" : "发送成功: " + response.data().messageId();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("飞书消息 content JSON 序列化失败", ex);
        }
    }

    public record MessageResponse(int code, String msg, MessageData data) {
    }

    public record MessageData(@JsonProperty("message_id") String messageId) {
    }
}

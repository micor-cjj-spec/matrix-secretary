package com.kailei.demo.channel.feishu.controller;

import com.kailei.demo.channel.core.ChannelTaskFacade;
import com.kailei.demo.channel.entity.ChannelEventLogEntity;
import com.kailei.demo.channel.feishu.adapter.FeishuChannelAdapter;
import com.kailei.demo.channel.feishu.config.FeishuProperties;
import com.kailei.demo.channel.model.ChannelIncomingMessage;
import com.kailei.demo.channel.repository.ChannelEventLogRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/feishu")
public class FeishuEventController {

    private final FeishuProperties properties;
    private final FeishuChannelAdapter adapter;
    private final ChannelTaskFacade taskFacade;
    private final ChannelEventLogRepository eventLogRepository;

    public FeishuEventController(FeishuProperties properties,
                                 FeishuChannelAdapter adapter,
                                 ChannelTaskFacade taskFacade,
                                 ChannelEventLogRepository eventLogRepository) {
        this.properties = properties;
        this.adapter = adapter;
        this.taskFacade = taskFacade;
        this.eventLogRepository = eventLogRepository;
    }

    @PostMapping("/events")
    public Map<String, Object> events(@RequestBody Map<String, Object> payload) {
        if (payload.containsKey("challenge")) {
            return Map.of("challenge", payload.get("challenge"));
        }
        if (!properties.isEnabled()) {
            return Map.of("code", 0, "msg", "feishu disabled");
        }
        String token = resolveToken(payload);
        if (properties.getVerificationToken() != null && !properties.getVerificationToken().isBlank()
                && !properties.getVerificationToken().equals(token)) {
            throw new IllegalArgumentException("飞书事件 token 校验失败");
        }

        String eventId = resolveEventId(payload);
        String tenantId = resolveTenantId(payload);
        if (eventLogRepository.existsByPlatformAndEventId("FEISHU", eventId)) {
            return Map.of("code", 0, "msg", "duplicated event ignored");
        }
        ChannelEventLogEntity log = eventLogRepository.createReceived("FEISHU", tenantId, eventId, eventLogRepository.toJson(payload));
        try {
            Optional<ChannelIncomingMessage> incoming = adapter.parseIncoming(payload);
            if (incoming.isEmpty()) {
                eventLogRepository.markIgnored(log.getId(), "unsupported or empty feishu event");
                return Map.of("code", 0, "msg", "ignored");
            }
            taskFacade.handleIncoming(incoming.get());
            eventLogRepository.markProcessed(log.getId(), incoming.get());
            return Map.of("code", 0, "msg", "ok");
        } catch (Exception ex) {
            eventLogRepository.markFailed(log.getId(), ex);
            throw ex;
        }
    }

    private String resolveEventId(Map<String, Object> payload) {
        Object headerRaw = payload.get("header");
        if (headerRaw instanceof Map<?, ?> header && header.get("event_id") != null) {
            return String.valueOf(header.get("event_id"));
        }
        Object uuid = payload.get("uuid");
        if (uuid != null) {
            return String.valueOf(uuid);
        }
        Object eventRaw = payload.get("event");
        if (eventRaw instanceof Map<?, ?> event) {
            Object messageRaw = event.get("message");
            if (messageRaw instanceof Map<?, ?> message && message.get("message_id") != null) {
                return String.valueOf(message.get("message_id"));
            }
        }
        return null;
    }

    private String resolveTenantId(Map<String, Object> payload) {
        Object headerRaw = payload.get("header");
        if (headerRaw instanceof Map<?, ?> header && header.get("tenant_key") != null) {
            return String.valueOf(header.get("tenant_key"));
        }
        Object tenantKey = payload.get("tenant_key");
        return tenantKey == null ? null : String.valueOf(tenantKey);
    }

    private String resolveToken(Map<String, Object> payload) {
        Object token = payload.get("token");
        if (token != null) {
            return String.valueOf(token);
        }
        Object headerRaw = payload.get("header");
        if (headerRaw instanceof Map<?, ?> header && header.get("token") != null) {
            return String.valueOf(header.get("token"));
        }
        return null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

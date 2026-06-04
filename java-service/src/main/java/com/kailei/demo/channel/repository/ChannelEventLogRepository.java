package com.kailei.demo.channel.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.channel.entity.ChannelEventLogEntity;
import com.kailei.demo.channel.mapper.ChannelEventLogMapper;
import com.kailei.demo.channel.model.ChannelIncomingMessage;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Repository
public class ChannelEventLogRepository {

    private final ChannelEventLogMapper mapper;
    private final ObjectMapper objectMapper;

    public ChannelEventLogRepository(ChannelEventLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public boolean existsByPlatformAndEventId(String platform, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        Long count = mapper.selectCount(new LambdaQueryWrapper<ChannelEventLogEntity>()
                .eq(ChannelEventLogEntity::getPlatform, platform)
                .eq(ChannelEventLogEntity::getEventId, eventId));
        return count != null && count > 0;
    }

    public ChannelEventLogEntity createReceived(String platform, String tenantId, String eventId, String rawPayload) {
        ChannelEventLogEntity entity = new ChannelEventLogEntity();
        entity.setId("che-" + UUID.randomUUID().toString().substring(0, 12));
        entity.setPlatform(platform);
        entity.setTenantId(tenantId);
        entity.setEventId(eventId);
        entity.setStatus("RECEIVED");
        entity.setRawPayload(rawPayload);
        entity.setCreatedAt(OffsetDateTime.now());
        mapper.insert(entity);
        return entity;
    }

    public void markProcessed(String id, ChannelIncomingMessage incoming) {
        ChannelEventLogEntity entity = mapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setMessageId(incoming.messageId());
        entity.setConversationId(incoming.conversationId());
        entity.setSenderId(incoming.senderId());
        entity.setStatus("PROCESSED");
        entity.setProcessedAt(OffsetDateTime.now());
        mapper.updateById(entity);
    }

    public void markIgnored(String id, String reason) {
        ChannelEventLogEntity entity = mapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setStatus("IGNORED");
        entity.setErrorMessage(reason);
        entity.setProcessedAt(OffsetDateTime.now());
        mapper.updateById(entity);
    }

    public void markFailed(String id, Exception ex) {
        ChannelEventLogEntity entity = mapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setStatus("FAILED");
        entity.setErrorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        entity.setProcessedAt(OffsetDateTime.now());
        mapper.updateById(entity);
    }

    public String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return String.valueOf(payload);
        }
    }
}

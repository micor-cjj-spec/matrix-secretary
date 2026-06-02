package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kailei.demo.entity.NotificationEntity;
import com.kailei.demo.mapper.NotificationMapper;
import com.kailei.demo.model.TaskAction;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationRepository {

    private final NotificationMapper mapper;

    public NotificationRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    public NotificationEntity createFromAction(String userId, String planId, TaskAction action, String type) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId("ntf-" + UUID.randomUUID().toString().substring(0, 12));
        entity.setUserId(userId);
        entity.setPlanId(planId);
        entity.setActionId(action.actionId());
        entity.setType(type == null || type.isBlank() ? action.actionType() : type);
        entity.setTitle(action.title() == null || action.title().isBlank() ? "AI秘书提醒" : limit(action.title(), 255));
        entity.setContent(action.content());
        entity.setStatus("UNREAD");
        entity.setScheduledAt(parseScheduledAt(action));
        entity.setCreatedAt(OffsetDateTime.now());
        mapper.insert(entity);
        return entity;
    }

    public List<NotificationEntity> findByUserId(String userId) {
        return mapper.selectList(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId, userId)
                .orderByDesc(NotificationEntity::getCreatedAt));
    }

    public List<NotificationEntity> findUnreadByUserId(String userId) {
        return mapper.selectList(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId, userId)
                .eq(NotificationEntity::getStatus, "UNREAD")
                .orderByDesc(NotificationEntity::getCreatedAt));
    }

    public NotificationEntity markRead(String notificationId, String userId) {
        NotificationEntity entity = mapper.selectById(notificationId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("通知不存在或无权访问: " + notificationId);
        }
        entity.setStatus("READ");
        entity.setReadAt(OffsetDateTime.now());
        mapper.updateById(entity);
        return entity;
    }

    private OffsetDateTime parseScheduledAt(TaskAction action) {
        if (action.schedule() == null || action.schedule().effectiveRunAt() == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(action.schedule().effectiveRunAt());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

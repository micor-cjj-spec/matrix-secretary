package com.kailei.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kailei.demo.entity.EmailDraftEntity;
import com.kailei.demo.mapper.EmailDraftMapper;
import com.kailei.demo.model.TaskAction;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class EmailDraftRepository {

    private final EmailDraftMapper mapper;

    public EmailDraftRepository(EmailDraftMapper mapper) {
        this.mapper = mapper;
    }

    public EmailDraftEntity createDraft(String userId, String planId, TaskAction action) {
        EmailDraftEntity draft = new EmailDraftEntity();
        draft.setId("draft-" + UUID.randomUUID().toString().substring(0, 12));
        draft.setUserId(userId);
        draft.setPlanId(planId);
        draft.setActionId(action.actionId());
        draft.setRecipientName(action.target() == null ? null : action.target().name());
        draft.setRecipientAddress(action.target() == null ? null : action.target().address());
        draft.setSubject(action.args() != null && action.args().containsKey("subject") ? action.args().get("subject").toString() : "无主题");
        draft.setBody(action.content());
        draft.setStatus("DRAFT");
        draft.setCreatedAt(OffsetDateTime.now());
        draft.setUpdatedAt(OffsetDateTime.now());
        mapper.insert(draft);
        return draft;
    }

    public List<EmailDraftEntity> findByUserId(String userId) {
        return mapper.selectList(new LambdaQueryWrapper<EmailDraftEntity>()
                .eq(EmailDraftEntity::getUserId, userId)
                .orderByDesc(EmailDraftEntity::getCreatedAt));
    }

    public EmailDraftEntity markSent(String draftId, String userId) {
        EmailDraftEntity draft = mapper.selectById(draftId);
        if (draft == null || !draft.getUserId().equals(userId)) {
            throw new IllegalArgumentException("草稿不存在或无权访问: " + draftId);
        }
        draft.setStatus("SENT");
        draft.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(draft);
        return draft;
    }
}

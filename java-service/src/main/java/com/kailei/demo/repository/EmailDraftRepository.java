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
        draft.setSubject(resolveSubject(action));
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
        return updateStatus(draftId, userId, "SENT");
    }

    public EmailDraftEntity markFailed(String draftId, String userId) {
        return updateStatus(draftId, userId, "FAILED");
    }

    private EmailDraftEntity updateStatus(String draftId, String userId, String status) {
        EmailDraftEntity draft = mapper.selectById(draftId);
        if (draft == null || !draft.getUserId().equals(userId)) {
            throw new IllegalArgumentException("草稿不存在或无权访问: " + draftId);
        }
        draft.setStatus(status);
        draft.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(draft);
        return draft;
    }

    private String resolveSubject(TaskAction action) {
        if (action.args() != null && action.args().containsKey("subject") && action.args().get("subject") != null) {
            return action.args().get("subject").toString();
        }
        return action.title() == null || action.title().isBlank() ? "AI秘书邮件" : action.title();
    }
}

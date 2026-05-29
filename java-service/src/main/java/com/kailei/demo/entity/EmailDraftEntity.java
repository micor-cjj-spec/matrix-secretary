package com.kailei.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.dromara.autotable.annotation.AutoTable;
import org.dromara.autotable.annotation.ColumnComment;
import org.dromara.autotable.annotation.ColumnType;
import org.dromara.autotable.annotation.Index;
import org.dromara.autotable.annotation.PrimaryKey;
import org.dromara.autotable.annotation.mysql.MysqlTypeConstant;

import java.time.OffsetDateTime;

@TableName("ai_email_draft")
@AutoTable(value = "ai_email_draft", comment = "AI秘书邮件草稿表")
public class EmailDraftEntity {

    @PrimaryKey
    @TableId(value = "id", type = IdType.INPUT)
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("邮件草稿ID")
    private String id;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("所属用户ID")
    private String userId;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("任务计划ID")
    private String planId;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("任务动作ID")
    private String actionId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 255)
    @ColumnComment("收件人名称")
    private String recipientName;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 255)
    @ColumnComment("收件人地址")
    private String recipientAddress;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 255)
    @ColumnComment("邮件主题")
    private String subject;

    @ColumnType(MysqlTypeConstant.TEXT)
    @ColumnComment("邮件正文")
    private String body;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("草稿状态")
    private String status;

    @ColumnComment("创建时间")
    private OffsetDateTime createdAt;

    @ColumnComment("更新时间")
    private OffsetDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

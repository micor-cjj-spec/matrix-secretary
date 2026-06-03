package com.kailei.demo.channel.entity;

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

@TableName("ai_channel_event_log")
@AutoTable(value = "ai_channel_event_log", comment = "AI秘书渠道事件日志表")
public class ChannelEventLogEntity {
    @PrimaryKey
    @TableId(value = "id", type = IdType.INPUT)
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("主键ID")
    private String id;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("渠道平台")
    private String platform;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("租户ID")
    private String tenantId;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("平台事件ID")
    private String eventId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("平台消息ID")
    private String messageId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("会话ID")
    private String conversationId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("发送人ID")
    private String senderId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("处理状态")
    private String status;

    @ColumnType(MysqlTypeConstant.TEXT)
    @ColumnComment("错误信息")
    private String errorMessage;

    @ColumnType(MysqlTypeConstant.TEXT)
    @ColumnComment("原始事件JSON")
    private String rawPayload;

    @ColumnComment("创建时间")
    private OffsetDateTime createdAt;

    @ColumnComment("处理时间")
    private OffsetDateTime processedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}

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

@TableName("ai_session")
@AutoTable(value = "ai_session", comment = "AI秘书会话状态表")
public class AiSessionEntity {

    @PrimaryKey
    @TableId(value = "session_id", type = IdType.INPUT)
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("会话ID")
    private String sessionId;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("用户ID")
    private String userId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("最近任务计划ID")
    private String lastPlanId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("待处理任务动作ID")
    private String pendingActionId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("会话状态")
    private String status;

    @ColumnType(MysqlTypeConstant.LONGTEXT)
    @ColumnComment("最近用户输入")
    private String lastUserInput;

    @ColumnComment("创建时间")
    private OffsetDateTime createdAt;

    @ColumnComment("更新时间")
    private OffsetDateTime updatedAt;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getLastPlanId() { return lastPlanId; }
    public void setLastPlanId(String lastPlanId) { this.lastPlanId = lastPlanId; }
    public String getPendingActionId() { return pendingActionId; }
    public void setPendingActionId(String pendingActionId) { this.pendingActionId = pendingActionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastUserInput() { return lastUserInput; }
    public void setLastUserInput(String lastUserInput) { this.lastUserInput = lastUserInput; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

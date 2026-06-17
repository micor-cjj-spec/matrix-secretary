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

@TableName("ai_task_dispatch_record")
@AutoTable(value = "ai_task_dispatch_record", comment = "AI秘书任务调度幂等记录表")
public class TaskDispatchRecordEntity {

    @PrimaryKey
    @TableId(value = "id", type = IdType.INPUT)
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("调度记录ID")
    private String id;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("任务计划ID")
    private String planId;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("任务动作ID")
    private String actionId;

    @Index
    @ColumnComment("触发时间")
    private OffsetDateTime triggerAt;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 192)
    @ColumnComment("幂等键")
    private String idempotencyKey;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("调度状态")
    private String status;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("调度持有者")
    private String dispatchOwner;

    @ColumnComment("开始时间")
    private OffsetDateTime startedAt;

    @ColumnComment("结束时间")
    private OffsetDateTime finishedAt;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 1024)
    @ColumnComment("错误信息")
    private String errorMessage;

    @ColumnComment("创建时间")
    private OffsetDateTime createdAt;

    @ColumnComment("更新时间")
    private OffsetDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public OffsetDateTime getTriggerAt() { return triggerAt; }
    public void setTriggerAt(OffsetDateTime triggerAt) { this.triggerAt = triggerAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDispatchOwner() { return dispatchOwner; }
    public void setDispatchOwner(String dispatchOwner) { this.dispatchOwner = dispatchOwner; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

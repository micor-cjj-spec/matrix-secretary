package com.kailei.demo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import org.dromara.autotable.annotation.AutoTable;
import org.dromara.autotable.annotation.ColumnComment;
import org.dromara.autotable.annotation.ColumnType;
import org.dromara.autotable.annotation.Index;
import org.dromara.autotable.annotation.PrimaryKey;
import org.dromara.autotable.annotation.mysql.MysqlTypeConstant;

import java.time.OffsetDateTime;

@TableName("ai_task_action")
@AutoTable(value = "ai_task_action", comment = "AI秘书任务动作表")
public class TaskActionEntity {

    @PrimaryKey
    @TableId(value = "action_id", type = IdType.INPUT)
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("任务动作ID")
    private String actionId;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("任务计划ID")
    private String planId;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("动作类型")
    private String actionType;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("技能名称")
    private String skillName;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 255)
    @ColumnComment("任务标题")
    private String title;

    @ColumnType(MysqlTypeConstant.LONGTEXT)
    @ColumnComment("任务内容")
    private String content;

    @ColumnType(MysqlTypeConstant.JSON)
    @ColumnComment("目标对象JSON")
    private String targetJson;

    @ColumnType(MysqlTypeConstant.JSON)
    @ColumnComment("调度信息JSON")
    private String scheduleJson;

    @ColumnType(MysqlTypeConstant.JSON)
    @ColumnComment("技能参数JSON")
    private String argsJson;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("优先级")
    private String priority;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("风险等级")
    private String riskLevel;

    @ColumnComment("置信度")
    private Double confidence;

    @ColumnComment("是否需要确认")
    private Boolean requiresConfirmation;

    @ColumnType(MysqlTypeConstant.LONGTEXT)
    @ColumnComment("来源句子")
    private String sourceSentence;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 512)
    @ColumnComment("语义解析备注")
    private String analysisNote;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("动作状态")
    private String status;

    @Index
    @ColumnComment("下一次触发时间")
    private OffsetDateTime nextRunAt;

    @ColumnComment("上一次触发时间")
    private OffsetDateTime lastRunAt;

    @ColumnComment("已触发次数")
    private Integer triggerCount;

    @Index
    @ColumnComment("调度锁过期时间")
    private OffsetDateTime dispatchLockedUntil;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("调度锁持有者")
    private String dispatchOwner;

    @ColumnComment("调度抢占次数")
    private Integer dispatchAttempt;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 512)
    @ColumnComment("执行说明")
    private String executionNote;

    @ColumnComment("排序号")
    private Integer sortOrder;

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTargetJson() { return targetJson; }
    public void setTargetJson(String targetJson) { this.targetJson = targetJson; }
    public String getScheduleJson() { return scheduleJson; }
    public void setScheduleJson(String scheduleJson) { this.scheduleJson = scheduleJson; }
    public String getArgsJson() { return argsJson; }
    public void setArgsJson(String argsJson) { this.argsJson = argsJson; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Boolean getRequiresConfirmation() { return requiresConfirmation; }
    public void setRequiresConfirmation(Boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
    public String getSourceSentence() { return sourceSentence; }
    public void setSourceSentence(String sourceSentence) { this.sourceSentence = sourceSentence; }
    public String getAnalysisNote() { return analysisNote; }
    public void setAnalysisNote(String analysisNote) { this.analysisNote = analysisNote; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(OffsetDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public OffsetDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(OffsetDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public Integer getTriggerCount() { return triggerCount; }
    public void setTriggerCount(Integer triggerCount) { this.triggerCount = triggerCount; }
    public OffsetDateTime getDispatchLockedUntil() { return dispatchLockedUntil; }
    public void setDispatchLockedUntil(OffsetDateTime dispatchLockedUntil) { this.dispatchLockedUntil = dispatchLockedUntil; }
    public String getDispatchOwner() { return dispatchOwner; }
    public void setDispatchOwner(String dispatchOwner) { this.dispatchOwner = dispatchOwner; }
    public Integer getDispatchAttempt() { return dispatchAttempt; }
    public void setDispatchAttempt(Integer dispatchAttempt) { this.dispatchAttempt = dispatchAttempt; }
    public String getExecutionNote() { return executionNote; }
    public void setExecutionNote(String executionNote) { this.executionNote = executionNote; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}

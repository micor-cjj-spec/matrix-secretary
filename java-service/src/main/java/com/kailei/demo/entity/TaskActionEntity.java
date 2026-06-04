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

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("调度类型")
    private String scheduleType;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("一次性执行时间")
    private String runAt;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("下一次执行时间")
    private String nextRunAt;

    @Index
    @ColumnComment("下一次执行时间epoch毫秒")
    private Long nextRunAtEpochMs;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("上一次执行时间")
    private String lastRunAt;

    @ColumnComment("触发次数")
    private Integer triggerCount;

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
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    public String getRunAt() { return runAt; }
    public void setRunAt(String runAt) { this.runAt = runAt; }
    public String getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(String nextRunAt) { this.nextRunAt = nextRunAt; }
    public Long getNextRunAtEpochMs() { return nextRunAtEpochMs; }
    public void setNextRunAtEpochMs(Long nextRunAtEpochMs) { this.nextRunAtEpochMs = nextRunAtEpochMs; }
    public String getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(String lastRunAt) { this.lastRunAt = lastRunAt; }
    public Integer getTriggerCount() { return triggerCount; }
    public void setTriggerCount(Integer triggerCount) { this.triggerCount = triggerCount; }
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
    public String getExecutionNote() { return executionNote; }
    public void setExecutionNote(String executionNote) { this.executionNote = executionNote; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}

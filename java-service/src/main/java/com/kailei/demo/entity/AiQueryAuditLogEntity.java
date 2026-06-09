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

@TableName("ai_query_audit_log")
@AutoTable(value = "ai_query_audit_log", comment = "AI数据查询审计日志表")
public class AiQueryAuditLogEntity {

    @PrimaryKey
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @ColumnComment("主键ID")
    private Long id;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("用户ID")
    private String userId;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("数据源编码")
    private String datasourceCode;

    @ColumnType(MysqlTypeConstant.LONGTEXT)
    @ColumnComment("用户自然语言问题")
    private String question;

    @ColumnType(MysqlTypeConstant.LONGTEXT)
    @ColumnComment("生成SQL")
    private String generatedSql;

    @ColumnType(MysqlTypeConstant.LONGTEXT)
    @ColumnComment("最终执行SQL")
    private String finalSql;

    @ColumnComment("是否成功，1成功0失败")
    private Integer successFlag;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 1024)
    @ColumnComment("错误信息")
    private String errorMessage;

    @ColumnComment("返回行数")
    private Integer rowCount;

    @ColumnComment("耗时毫秒")
    private Long costMs;

    @ColumnComment("创建时间")
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDatasourceCode() { return datasourceCode; }
    public void setDatasourceCode(String datasourceCode) { this.datasourceCode = datasourceCode; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getGeneratedSql() { return generatedSql; }
    public void setGeneratedSql(String generatedSql) { this.generatedSql = generatedSql; }
    public String getFinalSql() { return finalSql; }
    public void setFinalSql(String finalSql) { this.finalSql = finalSql; }
    public Integer getSuccessFlag() { return successFlag; }
    public void setSuccessFlag(Integer successFlag) { this.successFlag = successFlag; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

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

@TableName("ai_schema_table")
@AutoTable(value = "ai_schema_table", comment = "AI数据查询表元数据")
public class AiSchemaTableEntity {

    @PrimaryKey
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @ColumnComment("主键ID")
    private Long id;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("数据源编码")
    private String datasourceCode;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("表名")
    private String tableName;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 512)
    @ColumnComment("表注释")
    private String tableComment;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("业务名称")
    private String businessName;

    @ColumnComment("是否允许AI查询，1是0否")
    private Integer queryEnabledFlag;

    @ColumnComment("创建时间")
    private OffsetDateTime createdAt;

    @ColumnComment("更新时间")
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDatasourceCode() { return datasourceCode; }
    public void setDatasourceCode(String datasourceCode) { this.datasourceCode = datasourceCode; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public Integer getQueryEnabledFlag() { return queryEnabledFlag; }
    public void setQueryEnabledFlag(Integer queryEnabledFlag) { this.queryEnabledFlag = queryEnabledFlag; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

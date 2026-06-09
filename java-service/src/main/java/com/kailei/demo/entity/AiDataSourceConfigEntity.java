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

@TableName("ai_datasource_config")
@AutoTable(value = "ai_datasource_config", comment = "AI数据查询数据源配置表")
public class AiDataSourceConfigEntity {

    @PrimaryKey
    @TableId(value = "datasource_code", type = IdType.INPUT)
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
    @ColumnComment("数据源编码")
    private String datasourceCode;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("数据源名称")
    private String datasourceName;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
    @ColumnComment("数据库类型，例如 mysql/postgresql/oracle/sqlserver")
    private String dbType;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 1024)
    @ColumnComment("JDBC连接地址")
    private String jdbcUrl;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("数据库用户名，必须使用只读账号")
    private String username;

    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 512)
    @ColumnComment("数据库密码，MVP阶段先使用环境隔离，后续替换为加密存储")
    private String password;

    @Index
    @ColumnType(value = MysqlTypeConstant.VARCHAR, length = 128)
    @ColumnComment("默认Schema或库名")
    private String schemaName;

    @ColumnComment("是否只读数据源，1是0否")
    private Integer readonlyFlag;

    @ColumnComment("是否启用，1是0否")
    private Integer enabledFlag;

    @ColumnComment("创建时间")
    private OffsetDateTime createdAt;

    @ColumnComment("更新时间")
    private OffsetDateTime updatedAt;

    public String getDatasourceCode() { return datasourceCode; }
    public void setDatasourceCode(String datasourceCode) { this.datasourceCode = datasourceCode; }
    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public Integer getReadonlyFlag() { return readonlyFlag; }
    public void setReadonlyFlag(Integer readonlyFlag) { this.readonlyFlag = readonlyFlag; }
    public Integer getEnabledFlag() { return enabledFlag; }
    public void setEnabledFlag(Integer enabledFlag) { this.enabledFlag = enabledFlag; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

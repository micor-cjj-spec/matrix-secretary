package com.kailei.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kailei.demo.entity.AiDataSourceConfigEntity;
import com.kailei.demo.entity.AiSchemaColumnEntity;
import com.kailei.demo.entity.AiSchemaTableEntity;
import com.kailei.demo.mapper.AiSchemaColumnMapper;
import com.kailei.demo.mapper.AiSchemaTableMapper;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AiSchemaMetadataService {

    private final AiDataSourceService dataSourceService;
    private final AiSchemaTableMapper schemaTableMapper;
    private final AiSchemaColumnMapper schemaColumnMapper;

    public AiSchemaMetadataService(AiDataSourceService dataSourceService,
                                   AiSchemaTableMapper schemaTableMapper,
                                   AiSchemaColumnMapper schemaColumnMapper) {
        this.dataSourceService = dataSourceService;
        this.schemaTableMapper = schemaTableMapper;
        this.schemaColumnMapper = schemaColumnMapper;
    }

    public int scan(String datasourceCode) {
        AiDataSourceConfigEntity dataSource = dataSourceService.requireEnabled(datasourceCode);
        OffsetDateTime now = OffsetDateTime.now();
        int tableCount = 0;
        try (Connection connection = DriverManager.getConnection(
                dataSource.getJdbcUrl(), dataSource.getUsername(), dataSource.getPassword())) {
            connection.setReadOnly(true);
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = dataSource.getSchemaName();
            try (ResultSet tables = metaData.getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName == null || tableName.startsWith("ai_")) {
                        continue;
                    }
                    upsertTable(datasourceCode, tableName, tables.getString("REMARKS"), now);
                    scanColumns(metaData, connection.getCatalog(), schema, datasourceCode, tableName, now);
                    tableCount++;
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("扫描数据库元数据失败: " + ex.getMessage(), ex);
        }
        return tableCount;
    }

    public List<AiSchemaTableEntity> listTables(String datasourceCode) {
        return schemaTableMapper.selectList(new LambdaQueryWrapper<AiSchemaTableEntity>()
                .eq(AiSchemaTableEntity::getDatasourceCode, datasourceCode)
                .orderByAsc(AiSchemaTableEntity::getTableName));
    }

    public List<AiSchemaColumnEntity> listColumns(String datasourceCode, String tableName) {
        return schemaColumnMapper.selectList(new LambdaQueryWrapper<AiSchemaColumnEntity>()
                .eq(AiSchemaColumnEntity::getDatasourceCode, datasourceCode)
                .eq(AiSchemaColumnEntity::getTableName, tableName)
                .orderByAsc(AiSchemaColumnEntity::getColumnName));
    }

    public AiSchemaTableEntity setTableQueryEnabled(String datasourceCode, String tableName, boolean enabled) {
        AiSchemaTableEntity existing = schemaTableMapper.selectOne(new LambdaQueryWrapper<AiSchemaTableEntity>()
                .eq(AiSchemaTableEntity::getDatasourceCode, datasourceCode)
                .eq(AiSchemaTableEntity::getTableName, tableName)
                .last("limit 1"));
        if (existing == null) {
            throw new IllegalArgumentException("表元数据不存在，请先扫描数据源: " + tableName);
        }
        existing.setQueryEnabledFlag(enabled ? 1 : 0);
        existing.setUpdatedAt(OffsetDateTime.now());
        schemaTableMapper.updateById(existing);
        return existing;
    }

    public Set<String> allowedTables(String datasourceCode) {
        Set<String> tables = new LinkedHashSet<>();
        schemaTableMapper.selectList(new LambdaQueryWrapper<AiSchemaTableEntity>()
                        .eq(AiSchemaTableEntity::getDatasourceCode, datasourceCode)
                        .eq(AiSchemaTableEntity::getQueryEnabledFlag, 1))
                .forEach(table -> tables.add(table.getTableName().toLowerCase(Locale.ROOT)));
        return tables;
    }

    private void upsertTable(String datasourceCode, String tableName, String tableComment, OffsetDateTime now) {
        AiSchemaTableEntity existing = schemaTableMapper.selectOne(new LambdaQueryWrapper<AiSchemaTableEntity>()
                .eq(AiSchemaTableEntity::getDatasourceCode, datasourceCode)
                .eq(AiSchemaTableEntity::getTableName, tableName)
                .last("limit 1"));
        if (existing == null) {
            AiSchemaTableEntity entity = new AiSchemaTableEntity();
            entity.setDatasourceCode(datasourceCode);
            entity.setTableName(tableName);
            entity.setTableComment(tableComment);
            entity.setBusinessName(tableComment);
            entity.setQueryEnabledFlag(0);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            schemaTableMapper.insert(entity);
            return;
        }
        schemaTableMapper.update(null, new LambdaUpdateWrapper<AiSchemaTableEntity>()
                .set(AiSchemaTableEntity::getTableComment, tableComment)
                .set(AiSchemaTableEntity::getUpdatedAt, now)
                .eq(AiSchemaTableEntity::getId, existing.getId()));
    }

    private void scanColumns(DatabaseMetaData metaData,
                             String catalog,
                             String schema,
                             String datasourceCode,
                             String tableName,
                             OffsetDateTime now) throws SQLException {
        try (ResultSet columns = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                String columnComment = columns.getString("REMARKS");
                upsertColumn(datasourceCode, tableName, columnName, columnType, columnComment, now);
            }
        }
    }

    private void upsertColumn(String datasourceCode,
                              String tableName,
                              String columnName,
                              String columnType,
                              String columnComment,
                              OffsetDateTime now) {
        AiSchemaColumnEntity existing = schemaColumnMapper.selectOne(new LambdaQueryWrapper<AiSchemaColumnEntity>()
                .eq(AiSchemaColumnEntity::getDatasourceCode, datasourceCode)
                .eq(AiSchemaColumnEntity::getTableName, tableName)
                .eq(AiSchemaColumnEntity::getColumnName, columnName)
                .last("limit 1"));
        if (existing == null) {
            AiSchemaColumnEntity entity = new AiSchemaColumnEntity();
            entity.setDatasourceCode(datasourceCode);
            entity.setTableName(tableName);
            entity.setColumnName(columnName);
            entity.setColumnType(columnType);
            entity.setColumnComment(columnComment);
            entity.setBusinessName(columnComment);
            entity.setQueryEnabledFlag(1);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            schemaColumnMapper.insert(entity);
            return;
        }
        schemaColumnMapper.update(null, new LambdaUpdateWrapper<AiSchemaColumnEntity>()
                .set(AiSchemaColumnEntity::getColumnType, columnType)
                .set(AiSchemaColumnEntity::getColumnComment, columnComment)
                .set(AiSchemaColumnEntity::getUpdatedAt, now)
                .eq(AiSchemaColumnEntity::getId, existing.getId()));
    }
}

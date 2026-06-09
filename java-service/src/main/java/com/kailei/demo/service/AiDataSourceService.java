package com.kailei.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kailei.demo.entity.AiDataSourceConfigEntity;
import com.kailei.demo.mapper.AiDataSourceConfigMapper;
import com.kailei.demo.model.AiDataSourceUpsertRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AiDataSourceService {

    private final AiDataSourceConfigMapper dataSourceConfigMapper;

    public AiDataSourceService(AiDataSourceConfigMapper dataSourceConfigMapper) {
        this.dataSourceConfigMapper = dataSourceConfigMapper;
    }

    public AiDataSourceConfigEntity upsert(AiDataSourceUpsertRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        AiDataSourceConfigEntity existing = dataSourceConfigMapper.selectById(request.datasourceCode());
        AiDataSourceConfigEntity entity = existing == null ? new AiDataSourceConfigEntity() : existing;
        entity.setDatasourceCode(request.datasourceCode());
        entity.setDatasourceName(request.datasourceName());
        entity.setDbType(request.dbType());
        entity.setJdbcUrl(request.jdbcUrl());
        entity.setUsername(request.username());
        entity.setPassword(request.password());
        entity.setSchemaName(request.schemaName());
        entity.setReadonlyFlag(1);
        entity.setEnabledFlag(1);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        if (existing == null) {
            dataSourceConfigMapper.insert(entity);
        } else {
            dataSourceConfigMapper.updateById(entity);
        }
        return maskPassword(entity);
    }

    public AiDataSourceConfigEntity requireEnabled(String datasourceCode) {
        AiDataSourceConfigEntity entity = dataSourceConfigMapper.selectById(datasourceCode);
        if (entity == null || !Integer.valueOf(1).equals(entity.getEnabledFlag())) {
            throw new IllegalArgumentException("数据源不存在或未启用: " + datasourceCode);
        }
        if (!Integer.valueOf(1).equals(entity.getReadonlyFlag())) {
            throw new IllegalArgumentException("数据源不是只读配置，禁止AI查询: " + datasourceCode);
        }
        return entity;
    }

    public List<AiDataSourceConfigEntity> list() {
        return dataSourceConfigMapper.selectList(new LambdaQueryWrapper<AiDataSourceConfigEntity>()
                        .orderByAsc(AiDataSourceConfigEntity::getDatasourceCode))
                .stream()
                .map(this::maskPassword)
                .toList();
    }

    private AiDataSourceConfigEntity maskPassword(AiDataSourceConfigEntity source) {
        AiDataSourceConfigEntity masked = new AiDataSourceConfigEntity();
        masked.setDatasourceCode(source.getDatasourceCode());
        masked.setDatasourceName(source.getDatasourceName());
        masked.setDbType(source.getDbType());
        masked.setJdbcUrl(source.getJdbcUrl());
        masked.setUsername(source.getUsername());
        masked.setPassword("******");
        masked.setSchemaName(source.getSchemaName());
        masked.setReadonlyFlag(source.getReadonlyFlag());
        masked.setEnabledFlag(source.getEnabledFlag());
        masked.setCreatedAt(source.getCreatedAt());
        masked.setUpdatedAt(source.getUpdatedAt());
        return masked;
    }
}

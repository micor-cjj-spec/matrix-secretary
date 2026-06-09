package com.kailei.demo.service;

import com.kailei.demo.entity.AiDataSourceConfigEntity;
import com.kailei.demo.model.AiQueryResponse;
import com.kailei.demo.model.AiSqlQueryRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AiQueryService {

    private final AiDataSourceService dataSourceService;
    private final AiSchemaMetadataService schemaMetadataService;
    private final AiSqlSafetyValidator sqlSafetyValidator;
    private final AiReadOnlySqlExecutor sqlExecutor;
    private final AiQueryAuditService auditService;

    public AiQueryService(AiDataSourceService dataSourceService,
                          AiSchemaMetadataService schemaMetadataService,
                          AiSqlSafetyValidator sqlSafetyValidator,
                          AiReadOnlySqlExecutor sqlExecutor,
                          AiQueryAuditService auditService) {
        this.dataSourceService = dataSourceService;
        this.schemaMetadataService = schemaMetadataService;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.sqlExecutor = sqlExecutor;
        this.auditService = auditService;
    }

    public AiQueryResponse query(AiSqlQueryRequest request) {
        long start = System.currentTimeMillis();
        String finalSql = null;
        try {
            AiDataSourceConfigEntity dataSource = dataSourceService.requireEnabled(request.datasourceCode());
            Set<String> allowedTables = schemaMetadataService.allowedTables(request.datasourceCode());
            finalSql = sqlSafetyValidator.validateAndNormalize(request.sql(), allowedTables, request.maxRows());
            AiReadOnlySqlExecutor.QueryResult result = sqlExecutor.query(dataSource, finalSql);
            long costMs = System.currentTimeMillis() - start;
            auditService.record(request.userId(), request.datasourceCode(), request.question(), request.sql(), finalSql,
                    true, null, result.rows().size(), costMs);
            String answer = buildAnswer(request.question(), result.rows().size(), costMs);
            String responseSql = Boolean.TRUE.equals(request.returnSql()) ? finalSql : null;
            return new AiQueryResponse(answer, responseSql, result.columns(), result.rows(),
                    List.of("当前MVP执行用户提供的SQL，后续接入LLM生成SQL", "查询仅允许命中已启用白名单表"),
                    0.75, result.rows().size(), costMs);
        } catch (RuntimeException ex) {
            long costMs = System.currentTimeMillis() - start;
            auditService.record(request.userId(), request.datasourceCode(), request.question(), request.sql(), finalSql,
                    false, ex.getMessage(), 0, costMs);
            throw ex;
        }
    }

    private String buildAnswer(String question, int rowCount, long costMs) {
        return "已完成受控只读查询，问题：" + question + "。本次返回 " + rowCount + " 行数据，耗时 " + costMs + "ms。";
    }
}

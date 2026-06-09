package com.kailei.demo.service;

import com.kailei.demo.entity.AiDataSourceConfigEntity;
import com.kailei.demo.entity.AiSchemaColumnEntity;
import com.kailei.demo.entity.AiSchemaTableEntity;
import com.kailei.demo.model.AiNlQueryRequest;
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
    private final AiRuleBasedSqlGenerator ruleBasedSqlGenerator;

    public AiQueryService(AiDataSourceService dataSourceService,
                          AiSchemaMetadataService schemaMetadataService,
                          AiSqlSafetyValidator sqlSafetyValidator,
                          AiReadOnlySqlExecutor sqlExecutor,
                          AiQueryAuditService auditService,
                          AiRuleBasedSqlGenerator ruleBasedSqlGenerator) {
        this.dataSourceService = dataSourceService;
        this.schemaMetadataService = schemaMetadataService;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.sqlExecutor = sqlExecutor;
        this.auditService = auditService;
        this.ruleBasedSqlGenerator = ruleBasedSqlGenerator;
    }

    public AiQueryResponse query(AiSqlQueryRequest request) {
        return execute(request.datasourceCode(), request.userId(), request.question(), request.sql(), request.maxRows(), request.returnSql(),
                List.of("当前接口执行用户提供的SQL", "查询仅允许命中已启用白名单表"), 0.75);
    }

    public AiQueryResponse nlQuery(AiNlQueryRequest request) {
        List<AiSchemaTableEntity> tables = schemaMetadataService.listTables(request.datasourceCode());
        List<AiSchemaColumnEntity> columns = tables.stream()
                .flatMap(table -> schemaMetadataService.listColumns(request.datasourceCode(), table.getTableName()).stream())
                .toList();
        AiRuleBasedSqlGenerator.GeneratedSql generated = ruleBasedSqlGenerator.generate(
                request.question(), tables, columns, request.maxRows());
        return execute(request.datasourceCode(), request.userId(), request.question(), generated.sql(), request.maxRows(), request.returnSql(),
                List.of("当前MVP使用规则型SQL生成器，后续可替换为Dify/LLM生成器", "识别表: " + generated.tableName(), "识别意图: " + generated.intent()),
                0.55);
    }

    private AiQueryResponse execute(String datasourceCode,
                                    String userId,
                                    String question,
                                    String generatedSql,
                                    Integer maxRows,
                                    Boolean returnSql,
                                    List<String> assumptions,
                                    double confidence) {
        long start = System.currentTimeMillis();
        String finalSql = null;
        try {
            AiDataSourceConfigEntity dataSource = dataSourceService.requireEnabled(datasourceCode);
            Set<String> allowedTables = schemaMetadataService.allowedTables(datasourceCode);
            finalSql = sqlSafetyValidator.validateAndNormalize(generatedSql, allowedTables, maxRows);
            AiReadOnlySqlExecutor.QueryResult result = sqlExecutor.query(dataSource, finalSql);
            long costMs = System.currentTimeMillis() - start;
            auditService.record(userId, datasourceCode, question, generatedSql, finalSql,
                    true, null, result.rows().size(), costMs);
            String answer = buildAnswer(question, result.rows().size(), costMs);
            String responseSql = Boolean.TRUE.equals(returnSql) ? finalSql : null;
            return new AiQueryResponse(answer, responseSql, result.columns(), result.rows(),
                    assumptions, confidence, result.rows().size(), costMs);
        } catch (RuntimeException ex) {
            long costMs = System.currentTimeMillis() - start;
            auditService.record(userId, datasourceCode, question, generatedSql, finalSql,
                    false, ex.getMessage(), 0, costMs);
            throw ex;
        }
    }

    private String buildAnswer(String question, int rowCount, long costMs) {
        return "已完成受控只读查询，问题：" + question + "。本次返回 " + rowCount + " 行数据，耗时 " + costMs + "ms。";
    }
}

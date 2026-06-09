package com.kailei.demo.controller;

import com.kailei.demo.entity.AiDataSourceConfigEntity;
import com.kailei.demo.entity.AiSchemaColumnEntity;
import com.kailei.demo.entity.AiSchemaTableEntity;
import com.kailei.demo.model.AiDataSourceUpsertRequest;
import com.kailei.demo.model.AiNlQueryRequest;
import com.kailei.demo.model.AiQueryResponse;
import com.kailei.demo.model.AiSqlQueryRequest;
import com.kailei.demo.model.AiTableAccessRequest;
import com.kailei.demo.service.AiDataSourceService;
import com.kailei.demo.service.AiQueryService;
import com.kailei.demo.service.AiSchemaMetadataService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-query")
public class AiQueryController {

    private final AiDataSourceService dataSourceService;
    private final AiSchemaMetadataService schemaMetadataService;
    private final AiQueryService aiQueryService;

    public AiQueryController(AiDataSourceService dataSourceService,
                             AiSchemaMetadataService schemaMetadataService,
                             AiQueryService aiQueryService) {
        this.dataSourceService = dataSourceService;
        this.schemaMetadataService = schemaMetadataService;
        this.aiQueryService = aiQueryService;
    }

    @PostMapping("/datasources")
    public AiDataSourceConfigEntity upsertDataSource(@Valid @RequestBody AiDataSourceUpsertRequest request) {
        return dataSourceService.upsert(request);
    }

    @GetMapping("/datasources")
    public List<AiDataSourceConfigEntity> listDataSources() {
        return dataSourceService.list();
    }

    @PostMapping("/datasources/{datasourceCode}/scan-schema")
    public Map<String, Object> scanSchema(@PathVariable String datasourceCode) {
        int tableCount = schemaMetadataService.scan(datasourceCode);
        return Map.of("datasourceCode", datasourceCode, "tableCount", tableCount);
    }

    @GetMapping("/datasources/{datasourceCode}/tables")
    public List<AiSchemaTableEntity> listTables(@PathVariable String datasourceCode) {
        return schemaMetadataService.listTables(datasourceCode);
    }

    @PatchMapping("/datasources/{datasourceCode}/tables/{tableName}/access")
    public AiSchemaTableEntity setTableAccess(@PathVariable String datasourceCode,
                                              @PathVariable String tableName,
                                              @RequestBody(required = false) AiTableAccessRequest request) {
        boolean enabled = request != null && Boolean.TRUE.equals(request.queryEnabled());
        return schemaMetadataService.setTableQueryEnabled(datasourceCode, tableName, enabled);
    }

    @GetMapping("/datasources/{datasourceCode}/tables/{tableName}/columns")
    public List<AiSchemaColumnEntity> listColumns(@PathVariable String datasourceCode,
                                                  @PathVariable String tableName) {
        return schemaMetadataService.listColumns(datasourceCode, tableName);
    }

    @PostMapping("/nl-query")
    public AiQueryResponse nlQuery(@Valid @RequestBody AiNlQueryRequest request) {
        return aiQueryService.nlQuery(request);
    }

    @PostMapping("/sql-query")
    public AiQueryResponse sqlQuery(@Valid @RequestBody AiSqlQueryRequest request) {
        return aiQueryService.query(request);
    }
}

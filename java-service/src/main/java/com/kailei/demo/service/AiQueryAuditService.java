package com.kailei.demo.service;

import com.kailei.demo.entity.AiQueryAuditLogEntity;
import com.kailei.demo.mapper.AiQueryAuditLogMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AiQueryAuditService {

    private final AiQueryAuditLogMapper auditLogMapper;

    public AiQueryAuditService(AiQueryAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(String userId,
                       String datasourceCode,
                       String question,
                       String generatedSql,
                       String finalSql,
                       boolean success,
                       String errorMessage,
                       int rowCount,
                       long costMs) {
        AiQueryAuditLogEntity entity = new AiQueryAuditLogEntity();
        entity.setUserId(userId);
        entity.setDatasourceCode(datasourceCode);
        entity.setQuestion(question);
        entity.setGeneratedSql(generatedSql);
        entity.setFinalSql(finalSql);
        entity.setSuccessFlag(success ? 1 : 0);
        entity.setErrorMessage(errorMessage);
        entity.setRowCount(rowCount);
        entity.setCostMs(costMs);
        entity.setCreatedAt(OffsetDateTime.now());
        auditLogMapper.insert(entity);
    }
}

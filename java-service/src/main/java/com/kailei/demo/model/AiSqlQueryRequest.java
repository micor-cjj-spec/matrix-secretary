package com.kailei.demo.model;

import jakarta.validation.constraints.NotBlank;

public record AiSqlQueryRequest(
        @NotBlank String datasourceCode,
        @NotBlank String question,
        @NotBlank String sql,
        String userId,
        Integer maxRows,
        Boolean returnSql
) {
}

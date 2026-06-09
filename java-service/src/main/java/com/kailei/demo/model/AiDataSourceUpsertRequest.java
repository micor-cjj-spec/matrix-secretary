package com.kailei.demo.model;

import jakarta.validation.constraints.NotBlank;

public record AiDataSourceUpsertRequest(
        @NotBlank String datasourceCode,
        @NotBlank String datasourceName,
        @NotBlank String dbType,
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password,
        String schemaName
) {
}

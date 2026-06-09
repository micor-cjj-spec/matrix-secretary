package com.kailei.demo.model;

import jakarta.validation.constraints.NotBlank;

public record AiNlQueryRequest(
        @NotBlank String datasourceCode,
        @NotBlank String question,
        String userId,
        Integer maxRows,
        Boolean returnSql
) {
}

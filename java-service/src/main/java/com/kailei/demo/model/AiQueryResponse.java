package com.kailei.demo.model;

import java.util.List;
import java.util.Map;

public record AiQueryResponse(
        String answer,
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        List<String> assumptions,
        double confidence,
        int rowCount,
        long costMs
) {
}

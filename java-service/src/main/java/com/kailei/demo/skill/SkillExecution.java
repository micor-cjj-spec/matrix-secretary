package com.kailei.demo.skill;

import java.util.Map;

public record SkillExecution(
        String type,
        String executor,
        String method,
        String url,
        Map<String, String> headers,
        Map<String, Object> query,
        Map<String, Object> body
) {

    public SkillExecution {
        type = type == null || type.isBlank() ? "noop" : type;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
        body = body == null ? Map.of() : Map.copyOf(body);
    }
}

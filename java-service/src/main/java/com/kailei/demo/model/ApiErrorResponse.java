package com.kailei.demo.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        String path,
        String traceId,
        OffsetDateTime timestamp,
        Map<String, String> details
) {
    public ApiErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiErrorResponse of(String code,
                                      String message,
                                      String path,
                                      String traceId) {
        return new ApiErrorResponse(code, message, path, traceId, OffsetDateTime.now(), Map.of());
    }

    public static ApiErrorResponse of(String code,
                                      String message,
                                      String path,
                                      String traceId,
                                      Map<String, String> details) {
        return new ApiErrorResponse(code, message, path, traceId, OffsetDateTime.now(), details);
    }
}

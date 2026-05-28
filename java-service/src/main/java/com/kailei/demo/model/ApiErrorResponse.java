package com.kailei.demo.model;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        OffsetDateTime timestamp
) {
    public static ApiErrorResponse of(String code, String message, String traceId) {
        return new ApiErrorResponse(code, message, traceId, OffsetDateTime.now());
    }
}

package com.kailei.demo.exception;

import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ApiErrorCode code;
    private final Map<String, String> details;

    public BusinessException(ApiErrorCode code) {
        this(code, null, null);
    }

    public BusinessException(ApiErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ApiErrorCode code, String message, Map<String, String> details) {
        super(message == null || message.isBlank() ? code.defaultMessage() : message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ApiErrorCode code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }
}

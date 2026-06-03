package com.kailei.demo.config;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.kailei.demo.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                                  HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                         HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", request, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                          HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                details.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", request, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                                 HttpServletRequest request) {
        Map<String, String> details = Map.of(ex.getParameterName(), "缺少必填请求参数");
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "缺少必填请求参数", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                         HttpServletRequest request) {
        Map<String, String> details = extractJsonErrorDetails(ex);
        return error(HttpStatus.BAD_REQUEST, "INVALID_JSON", "请求体 JSON 格式错误或字段类型不匹配", request, details);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("method", ex.getMethod());
        if (ex.getSupportedMethods() != null) {
            details.put("supportedMethods", String.join(",", ex.getSupportedMethods()));
        }
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不支持", request, details);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex,
                                                                 HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "接口不存在", request, null);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiErrorResponse> handleRestClient(RestClientException ex,
                                                             HttpServletRequest request) {
        log.warn("Remote service call failed", ex);
        return error(HttpStatus.BAD_GATEWAY, "REMOTE_SERVICE_UNAVAILABLE",
                "Remote service is unavailable. Please check Python, LLM, or channel provider configuration.",
                request, Map.of("exception", ex.getClass().getSimpleName()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabase(DataAccessException ex,
                                                           HttpServletRequest request) {
        log.warn("Database access failed", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "Database is unavailable. Please check MySQL status and connection configuration.",
                request, Map.of("exception", ex.getClass().getSimpleName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex,
                                                            HttpServletRequest request) {
        String traceId = traceId();
        log.error("Unexpected API error, traceId={}, path={}", traceId, request.getRequestURI(), ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "服务器内部错误，请稍后重试或联系管理员，traceId=" + traceId,
                request.getRequestURI(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status,
                                                   String code,
                                                   String message,
                                                   HttpServletRequest request,
                                                   Map<String, String> details) {
        String traceId = traceId();
        ApiErrorResponse body = ApiErrorResponse.of(
                code,
                message == null || message.isBlank() ? status.getReasonPhrase() : message,
                request.getRequestURI(),
                traceId,
                details == null ? Map.of() : details
        );
        if (status.is5xxServerError()) {
            log.error("API error, traceId={}, code={}, path={}, details={}", traceId, code, request.getRequestURI(), details);
        } else {
            log.warn("API error, traceId={}, code={}, path={}, message={}, details={}", traceId, code, request.getRequestURI(), message, details);
        }
        return ResponseEntity.status(status).body(body);
    }

    private String traceId() {
        return "err-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, String> extractJsonErrorDetails(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormatException) {
            String field = invalidFormatException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null && !name.isBlank())
                    .reduce((first, second) -> second)
                    .orElse("body");
            return Map.of(field, "字段类型不匹配: " + invalidFormatException.getValue());
        }
        return Map.of();
    }
}

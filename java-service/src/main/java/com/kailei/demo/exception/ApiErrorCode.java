package com.kailei.demo.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "业务参数错误"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "缺少必填请求参数"),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "请求体 JSON 格式错误或字段类型不匹配"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未授权"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "无权访问或操作该资源"),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "任务计划不存在"),
    TASK_ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "任务动作不存在"),
    TASK_STATE_INVALID(HttpStatus.BAD_REQUEST, "任务状态不允许执行该操作"),
    REMOTE_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "远程服务不可用"),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "数据库不可用"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ApiErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

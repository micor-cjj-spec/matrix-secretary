package com.kailei.demo.model;

public record CancelTaskRequest(
        String operatorUserId,
        String reason
) {
}

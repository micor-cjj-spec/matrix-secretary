package com.kailei.demo.model;

import jakarta.validation.constraints.NotBlank;

public record PreviewTaskRequest(
        @NotBlank String text,
        String timezone,
        String userId
) {
    public String effectiveTimezone() {
        return timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone;
    }
}

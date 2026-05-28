package com.kailei.demo.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record TaskTarget(
        @JsonAlias("target_type")
        String targetType,
        String name,
        String address
) {
}

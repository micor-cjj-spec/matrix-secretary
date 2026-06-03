package com.kailei.demo.channel.feishu.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kailei.demo.channel.feishu.config.FeishuProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Service
public class FeishuTokenService {

    private static final long REFRESH_SKEW_SECONDS = 300;

    private final FeishuProperties properties;
    private final RestClient restClient;

    private String cachedTenantAccessToken;
    private Instant expiresAt = Instant.EPOCH;

    public FeishuTokenService(FeishuProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public synchronized String tenantAccessToken() {
        if (!properties.hasCredentials()) {
            throw new IllegalStateException("飞书应用凭据未配置");
        }
        if (cachedTenantAccessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_SKEW_SECONDS))) {
            return cachedTenantAccessToken;
        }
        TokenResponse response = restClient.post()
                .uri(properties.getBaseUrl() + "/open-apis/auth/v3/tenant_access_token/internal")
                .body(Map.of(
                        "app_id", properties.getClientId(),
                        "app_secret", properties.getClientSecret()
                ))
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.code() != 0 || response.tenantAccessToken() == null || response.tenantAccessToken().isBlank()) {
            throw new IllegalStateException("获取飞书 tenant_access_token 失败: " + (response == null ? "empty response" : response.msg()));
        }
        cachedTenantAccessToken = response.tenantAccessToken();
        expiresAt = Instant.now().plusSeconds(Math.max(60, response.expire()));
        return cachedTenantAccessToken;
    }

    public record TokenResponse(
            int code,
            String msg,
            @JsonProperty("tenant_access_token") String tenantAccessToken,
            int expire
    ) {
    }
}

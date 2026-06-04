package com.kailei.demo.channel.feishu;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeishuProperties {

    @Value("${feishu.enabled:false}")
    private boolean enabled;

    @Value("${feishu.client-id:}")
    private String clientId;

    @Value("${feishu.client-secret:}")
    private String clientSecret;

    @Value("${feishu.verification-token:}")
    private String verificationToken;

    @Value("${feishu.encrypt-key:}")
    private String encryptKey;

    @Value("${feishu.base-url:https://open.feishu.cn}")
    private String baseUrl;

    @Value("${feishu.mock-send-enabled:false}")
    private boolean mockSendEnabled;

    public boolean enabled() {
        return enabled;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String verificationToken() {
        return verificationToken;
    }

    public String encryptKey() {
        return encryptKey;
    }

    public String baseUrl() {
        return baseUrl == null || baseUrl.isBlank()
                ? "https://open.feishu.cn"
                : baseUrl.replaceAll("/+$", "");
    }

    public boolean mockSendEnabled() {
        return mockSendEnabled;
    }
}

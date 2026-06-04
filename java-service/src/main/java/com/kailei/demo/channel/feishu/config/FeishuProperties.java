package com.kailei.demo.channel.feishu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {

    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String verificationToken;
    private String encryptKey;
    private String baseUrl = "https://open.feishu.cn";
    private boolean mockSendEnabled;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public String getEncryptKey() { return encryptKey; }
    public void setEncryptKey(String encryptKey) { this.encryptKey = encryptKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public boolean isMockSendEnabled() { return mockSendEnabled; }
    public void setMockSendEnabled(boolean mockSendEnabled) { this.mockSendEnabled = mockSendEnabled; }

    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}

package com.kailei.demo.skill;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class MatrixVoucherSkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(MatrixVoucherSkillExecutor.class);

    private final RestClient restClient;
    private final String matrixBaseUrl;
    private final String agentApiKey;
    private final String defaultTenantId;

    public MatrixVoucherSkillExecutor(RestClient restClient,
                                      @Value("${matrix.base-url}") String matrixBaseUrl,
                                      @Value("${matrix.agent-api-key:}") String agentApiKey,
                                      @Value("${matrix.tenant-id:default}") String defaultTenantId) {
        this.restClient = restClient;
        this.matrixBaseUrl = trimTrailingSlash(matrixBaseUrl);
        this.agentApiKey = agentApiKey;
        this.defaultTenantId = defaultTenantId;
    }

    public TaskAction execute(String planId, String userId, TaskAction action) {
        String period = stringArg(action, "period");
        Integer page = intArg(action, "page", 1);
        Integer size = intArg(action, "size", 20);
        String summaryKeyword = stringArg(action, "summaryKeyword");
        String tenantId = valueOrDefault(stringArg(action, "tenantId"), defaultTenantId);
        String traceId = valueOrDefault(stringArg(action, "traceId"), planId + ":" + action.actionId());

        String url = UriComponentsBuilder
                .fromUriString(matrixBaseUrl + "/voucher/agent/pending-review")
                .queryParam("period", period)
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("summaryKeyword", optionalText(summaryKeyword))
                .build()
                .toUriString();

        try {
            Map<?, ?> response = restClient.get()
                    .uri(url)
                    .headers(headers -> {
                        headers.set("X-User-Id", valueOrDefault(userId, "anonymous"));
                        headers.set("X-Tenant-Id", tenantId);
                        headers.set("X-Trace-Id", traceId);
                        if (agentApiKey != null && !agentApiKey.isBlank()) {
                            headers.set("X-Matrix-Agent-Key", agentApiKey);
                        }
                    })
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return action.withStatus(TaskStatus.FAILED, "Matrix 未返回查询结果");
            }
            int code = intValue(response.get("code"), 500);
            if (code != 200) {
                return action.withStatus(TaskStatus.FAILED, "Matrix 查询失败: " + stringValue(response.get("message"), "未知错误"));
            }
            if (!(response.get("data") instanceof Map<?, ?> data)) {
                return action.withStatus(TaskStatus.FAILED, "Matrix 查询结果缺少 data");
            }

            int totalCount = intValue(data.get("totalCount"), 0);
            BigDecimal totalAmount = decimalValue(data.get("totalAmount"));
            int recordCount = data.get("records") instanceof List<?> records ? records.size() : 0;
            String resolvedPeriod = stringValue(data.get("period"), period);
            String warningText = warningText(data.get("warnings"));

            String note = "查询完成: 期间=" + resolvedPeriod
                    + ", 待审核凭证=" + totalCount + "张"
                    + ", 总金额=" + totalAmount.toPlainString()
                    + ", 本页明细=" + recordCount + "条";
            if (!warningText.isBlank()) {
                note += ", 提示=" + warningText;
            }
            log.info("Matrix voucher query executed planId={} actionId={} userId={} traceId={} totalCount={}",
                    planId, action.actionId(), userId, traceId, totalCount);
            return action.withStatus(TaskStatus.EXECUTED, note);
        } catch (RestClientResponseException ex) {
            log.warn("Matrix voucher query HTTP failure planId={} actionId={} status={}",
                    planId, action.actionId(), ex.getStatusCode(), ex);
            return action.withStatus(TaskStatus.FAILED,
                    "Matrix 查询失败: HTTP " + ex.getStatusCode().value() + ", " + safeBody(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            log.warn("Matrix voucher query failed planId={} actionId={}", planId, action.actionId(), ex);
            return action.withStatus(TaskStatus.FAILED, "Matrix 查询失败: " + ex.getClass().getSimpleName());
        }
    }

    private String stringArg(TaskAction action, String key) {
        Object value = action.args().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer intArg(TaskAction action, String key, int fallback) {
        Object value = action.args().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String warningText(Object value) {
        if (!(value instanceof List<?> warnings)) {
            return "";
        }
        return warnings.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf)
                .limit(3)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private String stringValue(Object value, String fallback) {
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private java.util.Optional<String> optionalText(String value) {
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }

    private String safeBody(String body) {
        if (body == null || body.isBlank()) {
            return "无响应内容";
        }
        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:10003/api";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MatrixMonthEndCloseCheckExecutor {

    private static final Logger log = LoggerFactory.getLogger(MatrixMonthEndCloseCheckExecutor.class);
    private static final int MAX_EXECUTION_NOTE_LENGTH = 500;

    private final RestClient restClient;
    private final String matrixBaseUrl;
    private final String agentApiKey;
    private final String defaultTenantId;

    public MatrixMonthEndCloseCheckExecutor(
            RestClient restClient,
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
        Long forg = longArg(action, "forg");
        String tenantId = valueOrDefault(stringArg(action, "tenantId"), defaultTenantId);
        String traceId = valueOrDefault(stringArg(action, "traceId"), planId + ":" + action.actionId());

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(matrixBaseUrl + "/period-process/agent/month-end-check");
        if (period != null && !period.isBlank()) {
            builder.queryParam("period", period);
        }
        if (forg != null) {
            builder.queryParam("forg", forg);
        }

        try {
            Map<?, ?> response = restClient.get()
                    .uri(builder.build().toUriString())
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
                return action.withStatus(TaskStatus.FAILED, "Matrix 未返回月结检查结果");
            }
            int code = intValue(response.get("code"), 500);
            if (code != 200) {
                return action.withStatus(TaskStatus.FAILED,
                        limit("Matrix 月结检查失败: " + stringValue(response.get("message"), "未知错误")));
            }
            if (!(response.get("data") instanceof Map<?, ?> data)) {
                return action.withStatus(TaskStatus.FAILED, "Matrix 月结检查结果缺少 data");
            }

            String resolvedPeriod = stringValue(data.get("period"), period == null ? "当前月份" : period);
            boolean canClose = booleanValue(data.get("canClose"));
            String closeStatus = stringValue(data.get("closeStatus"), canClose ? "READY" : "UNKNOWN");
            int readinessScore = intValue(data.get("readinessScore"), 0);
            int blockingCount = intValue(data.get("blockingCount"), 0);
            int warningCount = intValue(data.get("warningCount"), 0);
            int pendingCount = intValue(data.get("pendingCount"), 0);
            String itemPreview = checkItemPreview(data.get("checkItems"));
            String warningPreview = warningPreview(data.get("warnings"));

            String note = "月结检查完成: 期间=" + resolvedPeriod
                    + ", 是否可结账=" + (canClose ? "是" : "否")
                    + ", 状态=" + closeStatus
                    + ", 就绪度=" + readinessScore
                    + ", 阻塞=" + blockingCount
                    + ", 警告=" + warningCount
                    + ", 待处理=" + pendingCount;
            if (!itemPreview.isBlank()) {
                note += ", 关键检查=" + itemPreview;
            }
            if (!warningPreview.isBlank()) {
                note += ", 提示=" + warningPreview;
            }

            log.info("Matrix month-end check executed planId={} actionId={} userId={} traceId={} canClose={} status={}",
                    planId, action.actionId(), userId, traceId, canClose, closeStatus);
            return action.withStatus(TaskStatus.EXECUTED, limit(note));
        } catch (RestClientResponseException ex) {
            log.warn("Matrix month-end check HTTP failure planId={} actionId={} status={}",
                    planId, action.actionId(), ex.getStatusCode(), ex);
            return action.withStatus(TaskStatus.FAILED,
                    limit("Matrix 月结检查失败: HTTP " + ex.getStatusCode().value() + ", " + safeBody(ex.getResponseBodyAsString())));
        } catch (Exception ex) {
            log.warn("Matrix month-end check failed planId={} actionId={}", planId, action.actionId(), ex);
            return action.withStatus(TaskStatus.FAILED, "Matrix 月结检查失败: " + ex.getClass().getSimpleName());
        }
    }

    private String checkItemPreview(Object raw) {
        if (!(raw instanceof List<?> items)) {
            return "";
        }
        List<String> previews = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String status = stringValue(map.get("status"), "UNKNOWN");
            if ("PASSED".equalsIgnoreCase(status) && previews.size() >= 2) {
                continue;
            }
            String name = stringValue(map.get("name"), "检查项");
            String message = stringValue(map.get("message"), "");
            previews.add(name + "[" + status + "]" + (message.isBlank() ? "" : ":" + message));
            if (previews.size() >= 5) {
                break;
            }
        }
        return String.join("；", previews);
    }

    private String warningPreview(Object raw) {
        if (!(raw instanceof List<?> warnings)) {
            return "";
        }
        return warnings.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf)
                .limit(2)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private String stringArg(TaskAction action, String key) {
        Object value = action.args().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long longArg(TaskAction action, String key) {
        Object value = action.args().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String stringValue(Object value, String fallback) {
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeBody(String body) {
        if (body == null || body.isBlank()) {
            return "无响应内容";
        }
        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private String limit(String value) {
        if (value == null || value.length() <= MAX_EXECUTION_NOTE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_EXECUTION_NOTE_LENGTH - 3) + "...";
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

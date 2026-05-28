package com.kailei.demo.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GenericSkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(GenericSkillExecutor.class);

    private final RestClient restClient;
    private final SkillTemplateRenderer renderer;

    public GenericSkillExecutor(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.renderer = new SkillTemplateRenderer(objectMapper);
    }

    public TaskAction execute(SkillDefinition skill, TaskAction action) {
        String type = skill.execution().type();
        return switch (type) {
            case "builtin" -> executeBuiltin(skill, action);
            case "http" -> executeHttp(skill, action);
            case "prompt" -> action.withStatus(TaskStatus.EXECUTED, "已生成文本型任务: " + action.content());
            case "noop" -> action.withStatus(TaskStatus.EXECUTED, "已记录任务，等待人工处理: " + action.content());
            default -> action.withStatus(TaskStatus.FAILED, "不支持的 Skill 执行类型: " + type);
        };
    }

    private TaskAction executeBuiltin(SkillDefinition skill, TaskAction action) {
        String executor = skill.execution().executor();
        String note = switch (executor == null ? "" : executor) {
            case "email" -> "模拟发送邮件: " + action.content();
            case "message" -> "模拟发送消息: " + action.content();
            case "reply" -> "模拟回复消息: " + action.content();
            case "reminder" -> "模拟创建提醒: " + action.content();
            case "todo" -> "模拟创建待办: " + action.content();
            case "schedule" -> "模拟创建定时任务: " + action.content();
            default -> "模拟执行 Skill[" + skill.name() + "]: " + action.content();
        };
        log.info("Execute skill [{}] action [{}] {}", skill.name(), action.actionId(), note);
        return action.withStatus(TaskStatus.EXECUTED, note);
    }

    private TaskAction executeHttp(SkillDefinition skill, TaskAction action) {
        SkillExecution execution = skill.execution();
        if (execution.url() == null || execution.url().isBlank()) {
            return action.withStatus(TaskStatus.FAILED, "HTTP Skill 缺少 url: " + skill.name());
        }
        String url = renderer.renderString(execution.url(), action);
        Map<String, Object> query = renderer.renderMap(execution.query(), action);
        Map<String, Object> body = renderer.renderMap(execution.body(), action);
        String method = execution.method() == null ? "GET" : execution.method().toUpperCase();
        try {
            Object response;
            if ("POST".equals(method)) {
                response = restClient.post()
                        .uri(buildUrl(url, query))
                        .headers(headers -> execution.headers().forEach(headers::add))
                        .body(body.isEmpty() ? buildDefaultBody(action) : body)
                        .retrieve()
                        .body(Object.class);
            } else {
                response = restClient.get()
                        .uri(buildUrl(url, query))
                        .headers(headers -> execution.headers().forEach(headers::add))
                        .retrieve()
                        .body(Object.class);
            }
            log.info("HTTP skill [{}] action [{}] called {}", skill.name(), action.actionId(), url);
            return action.withStatus(TaskStatus.EXECUTED, "HTTP Skill 调用成功: " + response);
        } catch (Exception ex) {
            log.warn("HTTP skill [{}] action [{}] failed", skill.name(), action.actionId(), ex);
            return action.withStatus(TaskStatus.FAILED, "HTTP Skill 调用失败: " + ex.getClass().getSimpleName());
        }
    }

    private String buildUrl(String url, Map<String, Object> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        query.forEach(builder::queryParam);
        return builder.build().toUriString();
    }

    private Map<String, Object> buildDefaultBody(TaskAction action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actionId", action.actionId());
        body.put("actionType", action.actionType());
        body.put("skillName", action.skillName());
        body.put("title", action.title());
        body.put("content", action.content());
        body.put("target", action.target());
        body.put("schedule", action.schedule());
        body.put("args", action.args());
        return body;
    }
}

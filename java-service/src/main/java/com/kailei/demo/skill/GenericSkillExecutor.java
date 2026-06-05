package com.kailei.demo.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.channel.core.ChannelMessageExecutor;
import com.kailei.demo.entity.EmailDraftEntity;
import com.kailei.demo.entity.NotificationEntity;
import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.repository.EmailDraftRepository;
import com.kailei.demo.repository.NotificationRepository;
import com.kailei.demo.service.EmailSandboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final SkillArgumentValidator validator = new SkillArgumentValidator();
    private final NotificationRepository notificationRepository;
    private final EmailDraftRepository emailDraftRepository;
    private final EmailSandboxService emailSandboxService;
    private final ChannelMessageExecutor channelMessageExecutor;
    private final boolean httpSkillEnabled;

    public GenericSkillExecutor(RestClient restClient,
                                ObjectMapper objectMapper,
                                NotificationRepository notificationRepository,
                                EmailDraftRepository emailDraftRepository,
                                EmailSandboxService emailSandboxService,
                                ChannelMessageExecutor channelMessageExecutor,
                                @Value("${ai-secretary.http-skill.enabled:false}") boolean httpSkillEnabled) {
        this.restClient = restClient;
        this.renderer = new SkillTemplateRenderer(objectMapper);
        this.notificationRepository = notificationRepository;
        this.emailDraftRepository = emailDraftRepository;
        this.emailSandboxService = emailSandboxService;
        this.channelMessageExecutor = channelMessageExecutor;
        this.httpSkillEnabled = httpSkillEnabled;
    }

    public TaskAction execute(String planId, String userId, SkillDefinition skill, TaskAction action) {
        try {
            validator.validate(skill, action);
        } catch (IllegalArgumentException ex) {
            return action.withStatus(TaskStatus.NEEDS_MANUAL_REVIEW, "参数校验失败，需要人工处理: " + ex.getMessage());
        }
        String type = skill.execution().type();
        return switch (type) {
            case "builtin" -> executeBuiltin(planId, userId, skill, action);
            case "http" -> {
                if (!httpSkillEnabled) {
                    yield action.withStatus(
                            TaskStatus.FAILED_FINAL,
                            "HTTP Skill 默认关闭；如需启用，请先配置 URL 白名单、内网地址拦截和超时策略"
                    );
                }
                yield executeHttp(skill, action);
            }
            case "prompt" -> action.withStatus(TaskStatus.EXECUTED, "已生成文本型任务: " + action.content());
            case "noop" -> action.withStatus(TaskStatus.EXECUTED, "已记录任务，等待人工处理: " + action.content());
            default -> action.withStatus(TaskStatus.FAILED_FINAL, "不支持的 Skill 执行类型: " + type);
        };
    }

    private TaskAction executeBuiltin(String planId, String userId, SkillDefinition skill, TaskAction action) {
        String executor = skill.execution().executor();
        return switch (executor == null ? "" : executor) {
            case "reminder" -> executeReminder(planId, userId, skill, action);
            case "todo" -> createNotification(planId, userId, action, "TODO", "已创建站内待办");
            case "email" -> createEmailDraftAndMaybeSend(planId, userId, action);
            case "message", "reply" -> executeChannelMessage(planId, userId, skill, action);
            case "schedule" -> mockExecuted(skill, action, "模拟创建定时任务: " + action.content());
            default -> action.withStatus(TaskStatus.FAILED_FINAL, "不支持的 builtin executor: " + executor);
        };
    }

    private TaskAction executeReminder(String planId, String userId, SkillDefinition skill, TaskAction action) {
        if (isFeishuUser(userId) || "feishu".equalsIgnoreCase(stringArg(action, "platform"))) {
            TaskAction channelAction = withReminderMessage(action);
            return executeChannelMessage(planId, userId, skill, channelAction);
        }
        return createNotification(planId, userId, action, "REMINDER", "已创建站内提醒");
    }

    private TaskAction executeChannelMessage(String planId, String userId, SkillDefinition skill, TaskAction action) {
        try {
            return channelMessageExecutor.send(planId, userId, action);
        } catch (IllegalArgumentException ex) {
            log.warn("Channel message skill [{}] action [{}] invalid args", skill.name(), action.actionId(), ex);
            return action.withStatus(TaskStatus.NEEDS_MANUAL_REVIEW, "渠道消息参数错误，需要人工处理: " + ex.getMessage());
        } catch (Exception ex) {
            log.warn("Channel message skill [{}] action [{}] failed", skill.name(), action.actionId(), ex);
            return action.withStatus(TaskStatus.FAILED, "渠道消息发送失败: " + ex.getClass().getSimpleName());
        }
    }

    private TaskAction withReminderMessage(TaskAction action) {
        String content = action.content();
        String message = content == null || content.isBlank() ? "该处理提醒事项了" : content;
        if ("喝水".equals(message) || message.startsWith("喝水")) {
            message = "该喝水了";
        }
        return action.withEditableFields(null, message, null, null, null, null, null);
    }

    private boolean isFeishuUser(String userId) {
        return userId != null && userId.startsWith("feishu:");
    }

    private String stringArg(TaskAction action, String key) {
        Object value = action.args().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private TaskAction createNotification(String planId, String userId, TaskAction action, String type, String prefix) {
        if (userId == null || userId.isBlank()) {
            return action.withStatus(TaskStatus.NEEDS_MANUAL_REVIEW, "创建站内通知失败: 缺少 userId，需要人工处理");
        }
        NotificationEntity notification = notificationRepository.createFromAction(userId, planId, action, type);
        String note = prefix + ": notificationId=" + notification.getId();
        log.info("Create notification [{}] for action [{}] user [{}]", notification.getId(), action.actionId(), userId);
        return action.withStatus(TaskStatus.EXECUTED, note);
    }

    private TaskAction createEmailDraftAndMaybeSend(String planId, String userId, TaskAction action) {
        if (userId == null || userId.isBlank()) {
            return action.withStatus(TaskStatus.NEEDS_MANUAL_REVIEW, "创建邮件草稿失败: 缺少 userId，需要人工处理");
        }
        EmailDraftEntity draft = emailDraftRepository.createDraft(userId, planId, action);
        EmailSandboxService.SendResult sendResult = emailSandboxService.sendDraftToSandbox(draft);
        if (sendResult.attempted() && sendResult.sent()) {
            emailDraftRepository.markSent(draft.getId(), userId);
        } else if (sendResult.attempted()) {
            emailDraftRepository.markFailed(draft.getId(), userId);
            return action.withStatus(TaskStatus.FAILED, "邮件草稿已创建但发送失败: draftId=" + draft.getId() + ", " + sendResult.message());
        }

        String note = "已创建邮件草稿: draftId=" + draft.getId() + ", " + sendResult.message();
        if (draft.getRecipientAddress() == null || draft.getRecipientAddress().isBlank()) {
            note += ", 解析收件人地址为空但测试模式会发往沙箱收件人";
        }
        log.info("Create email draft [{}] for action [{}] user [{}] sendResult={}", draft.getId(), action.actionId(), userId, sendResult.message());
        return action.withStatus(TaskStatus.EXECUTED, note);
    }

    private TaskAction mockExecuted(SkillDefinition skill, TaskAction action, String note) {
        log.info("Execute skill [{}] action [{}] {}", skill.name(), action.actionId(), note);
        return action.withStatus(TaskStatus.EXECUTED, note);
    }

    private TaskAction executeHttp(SkillDefinition skill, TaskAction action) {
        SkillExecution execution = skill.execution();
        if (execution.url() == null || execution.url().isBlank()) {
            return action.withStatus(TaskStatus.FAILED_FINAL, "HTTP Skill 缺少 url: " + skill.name());
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
        } catch (IllegalArgumentException ex) {
            log.warn("HTTP skill [{}] action [{}] invalid args", skill.name(), action.actionId(), ex);
            return action.withStatus(TaskStatus.NEEDS_MANUAL_REVIEW, "HTTP Skill 参数错误，需要人工处理: " + ex.getMessage());
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

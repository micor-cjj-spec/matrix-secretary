package com.kailei.demo.service;

import com.kailei.demo.entity.EmailDraftEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSandboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailSandboxService.class);

    private final JavaMailSender mailSender;
    private final boolean sendEnabled;
    private final String testRecipient;
    private final String mailUsername;

    public EmailSandboxService(JavaMailSender mailSender,
                               @Value("${ai-secretary.email.send-enabled:false}") boolean sendEnabled,
                               @Value("${ai-secretary.email.test-recipient:}") String testRecipient,
                               @Value("${spring.mail.username:}") String mailUsername) {
        this.mailSender = mailSender;
        this.sendEnabled = sendEnabled;
        this.testRecipient = testRecipient == null ? "" : testRecipient.trim();
        this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
    }

    public SendResult sendDraftToSandbox(EmailDraftEntity draft) {
        if (!sendEnabled) {
            return SendResult.skipped("邮件发送未启用，已仅创建草稿");
        }
        if (testRecipient.isBlank()) {
            return SendResult.failed("邮件沙箱收件人未配置: EMAIL_TEST_RECIPIENT");
        }
        if (mailUsername.isBlank()) {
            return SendResult.failed("邮件发送账号未配置: MAIL_USERNAME");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(testRecipient);
        message.setSubject(safeSubject(draft));
        message.setText(buildSandboxBody(draft));
        try {
            mailSender.send(message);
            log.info("Sandbox email sent, draftId={}, testRecipient={}", draft.getId(), testRecipient);
            return SendResult.sent("测试邮件已发送到: " + testRecipient);
        } catch (MailException ex) {
            log.warn("Sandbox email send failed, draftId={}", draft.getId(), ex);
            return SendResult.failed("测试邮件发送失败: " + ex.getClass().getSimpleName());
        }
    }

    private String safeSubject(EmailDraftEntity draft) {
        String subject = draft.getSubject() == null || draft.getSubject().isBlank() ? "AI秘书测试邮件" : draft.getSubject();
        return "[AI秘书测试] " + subject;
    }

    private String buildSandboxBody(EmailDraftEntity draft) {
        return "【AI秘书测试模式】\n"
                + "本邮件由 matrix-secretary 测试环境发送。\n"
                + "真实解析收件人名称: " + nullToEmpty(draft.getRecipientName()) + "\n"
                + "真实解析收件人地址: " + nullToEmpty(draft.getRecipientAddress()) + "\n"
                + "planId: " + nullToEmpty(draft.getPlanId()) + "\n"
                + "actionId: " + nullToEmpty(draft.getActionId()) + "\n\n"
                + "----- 原始邮件正文 -----\n"
                + nullToEmpty(draft.getBody());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record SendResult(boolean attempted, boolean sent, String message) {
        public static SendResult skipped(String message) {
            return new SendResult(false, false, message);
        }

        public static SendResult sent(String message) {
            return new SendResult(true, true, message);
        }

        public static SendResult failed(String message) {
            return new SendResult(true, false, message);
        }
    }
}

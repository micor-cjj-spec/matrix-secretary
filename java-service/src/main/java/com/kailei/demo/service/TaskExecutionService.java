package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    public TaskAction confirmAction(TaskAction action) {
        if (action.schedule() != null && action.schedule().isScheduled()) {
            String note = "已进入调度队列: " + (action.schedule().runAt() != null ? action.schedule().runAt() : action.schedule().cron());
            log.info("Schedule action [{}] {}", action.actionId(), note);
            return action.withStatus(TaskStatus.SCHEDULED, note);
        }
        return executeNow(action);
    }

    public TaskAction executeNow(TaskAction action) {
        String note = switch (action.actionType()) {
            case "send_email" -> "模拟发送邮件: " + action.content();
            case "send_message" -> "模拟发送消息: " + action.content();
            case "reply_message" -> "模拟回复消息: " + action.content();
            case "reminder" -> "模拟创建提醒: " + action.content();
            case "create_todo" -> "模拟创建待办: " + action.content();
            default -> "模拟执行任务: " + action.content();
        };
        log.info("Execute action [{}] {}", action.actionId(), note);
        return action.withStatus(TaskStatus.EXECUTED, note);
    }
}

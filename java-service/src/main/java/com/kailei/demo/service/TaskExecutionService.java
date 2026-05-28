package com.kailei.demo.service;

import com.kailei.demo.model.TaskAction;
import com.kailei.demo.model.TaskStatus;
import com.kailei.demo.skill.GenericSkillExecutor;
import com.kailei.demo.skill.SkillCatalog;
import com.kailei.demo.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final SkillCatalog skillCatalog;
    private final GenericSkillExecutor genericSkillExecutor;

    public TaskExecutionService(SkillCatalog skillCatalog, GenericSkillExecutor genericSkillExecutor) {
        this.skillCatalog = skillCatalog;
        this.genericSkillExecutor = genericSkillExecutor;
    }

    public TaskAction confirmAction(TaskAction action) {
        if (action.schedule() != null && action.schedule().isScheduled()) {
            String note = "已进入调度队列: " + (action.schedule().runAt() != null ? action.schedule().runAt() : action.schedule().cron());
            log.info("Schedule action [{}] {}", action.actionId(), note);
            return action.withStatus(TaskStatus.SCHEDULED, note);
        }
        return executeNow(action);
    }

    public TaskAction executeNow(TaskAction action) {
        SkillDefinition skill = skillCatalog.getOrUnknown(action.actionType());
        return genericSkillExecutor.execute(skill, action);
    }
}

package com.kailei.demo.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskActionTest {

    @Test
    void withEditableFieldsOnlyUpdatesUserEditableFields() {
        TaskAction original = action();
        TaskTarget nextTarget = new TaskTarget("user", "王总", null);
        TaskSchedule nextSchedule = new TaskSchedule(
                "once",
                "明天下午三点",
                "2026-06-23T15:00:00+08:00",
                "0 0 15 23 6 ? 2026",
                "Asia/Shanghai",
                "2026-06-23T15:00:00+08:00",
                null,
                0
        );

        TaskAction edited = original.withEditableFields(
                "提醒王总确认方案",
                "确认项目方案已经更新",
                nextTarget,
                nextSchedule,
                Map.of("source", "manual_edit"),
                "high",
                true
        );

        assertThat(edited.title()).isEqualTo("提醒王总确认方案");
        assertThat(edited.content()).isEqualTo("确认项目方案已经更新");
        assertThat(edited.target()).isEqualTo(nextTarget);
        assertThat(edited.schedule()).isEqualTo(nextSchedule);
        assertThat(edited.args()).containsEntry("source", "manual_edit");
        assertThat(edited.priority()).isEqualTo("high");
        assertThat(edited.requiresConfirmation()).isTrue();

        assertThat(edited.actionId()).isEqualTo(original.actionId());
        assertThat(edited.actionType()).isEqualTo(original.actionType());
        assertThat(edited.skillName()).isEqualTo(original.skillName());
        assertThat(edited.riskLevel()).isEqualTo(original.riskLevel());
        assertThat(edited.confidence()).isEqualTo(original.confidence());
        assertThat(edited.sourceSentence()).isEqualTo(original.sourceSentence());
        assertThat(edited.analysisNote()).isEqualTo(original.analysisNote());
        assertThat(edited.status()).isEqualTo(original.status());
        assertThat(edited.executionNote()).isEqualTo(original.executionNote());
    }

    @Test
    void withEditableFieldsKeepsCurrentValueWhenTextPatchIsBlank() {
        TaskAction original = action();

        TaskAction edited = original.withEditableFields(
                " ",
                null,
                null,
                null,
                null,
                "",
                null
        );

        assertThat(edited.title()).isEqualTo(original.title());
        assertThat(edited.content()).isEqualTo(original.content());
        assertThat(edited.target()).isEqualTo(original.target());
        assertThat(edited.schedule()).isEqualTo(original.schedule());
        assertThat(edited.args()).isEqualTo(original.args());
        assertThat(edited.priority()).isEqualTo(original.priority());
        assertThat(edited.requiresConfirmation()).isEqualTo(original.requiresConfirmation());
    }

    @Test
    void constructorNormalizesNullArgsRiskLevelAndConfirmationFlag() {
        TaskAction action = new TaskAction(
                "action-test",
                "reminder",
                "reminder",
                "提醒事项",
                "整理项目进度",
                new TaskTarget("self", "我", null),
                null,
                null,
                "normal",
                null,
                0.8,
                null,
                "提醒我整理项目进度",
                "测试解析备注",
                TaskStatus.WAITING_CONFIRM,
                "等待确认"
        );

        assertThat(action.args()).isEmpty();
        assertThat(action.riskLevel()).isEqualTo("LOW");
        assertThat(action.requiresConfirmation()).isFalse();
    }

    private static TaskAction action() {
        return new TaskAction(
                "action-test",
                "reminder",
                "reminder",
                "提醒事项",
                "确认合同盖章",
                new TaskTarget("user", "李雷", null),
                new TaskSchedule("none", null, null, null, "Asia/Shanghai", null, null, 0),
                Map.of("source", "llm"),
                "normal",
                "MEDIUM",
                0.91,
                false,
                "明天下午三点提醒我给李雷确认合同盖章",
                "测试解析备注",
                TaskStatus.WAITING_CONFIRM,
                "等待用户确认"
        );
    }
}

package com.kailei.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class TaskCenterSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskCenterSchemaInitializer.class);
    private static final int BACKFILL_BATCH_SIZE = 500;
    private static final int BACKFILL_MAX_ROUNDS = 20;

    private final JdbcTemplate jdbcTemplate;

    public TaskCenterSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexes();
        backfillTaskActionScheduleAndIdempotencyFields();
    }

    private void ensureIndexes() {
        ensureIndex(
                "ai_task_action_execution",
                "uk_task_action_execution_idempotency_key",
                "alter table ai_task_action_execution "
                        + "add unique key uk_task_action_execution_idempotency_key (idempotency_key)"
        );
        ensureIndex(
                "ai_task_action",
                "idx_task_action_status_next_run_epoch",
                "alter table ai_task_action "
                        + "add index idx_task_action_status_next_run_epoch (status, next_run_at_epoch_ms)"
        );
        ensureIndex(
                "ai_task_action",
                "idx_task_action_status_lock_next_run",
                "alter table ai_task_action "
                        + "add index idx_task_action_status_lock_next_run "
                        + "(status, locked_by, locked_at_epoch_ms, next_run_at_epoch_ms)"
        );
        ensureIndex(
                "ai_task_action",
                "idx_task_action_plan_status",
                "alter table ai_task_action "
                        + "add index idx_task_action_plan_status (plan_id, status)"
        );
        ensureIndex(
                "ai_task_action",
                "idx_task_action_status_retry_next_run",
                "alter table ai_task_action "
                        + "add index idx_task_action_status_retry_next_run "
                        + "(status, next_retry_at_epoch_ms, next_run_at_epoch_ms)"
        );
    }

    private void ensureIndex(String tableName, String indexName, String ddl) {
        try {
            Integer exists = jdbcTemplate.queryForObject("""
                    select count(1)
                    from information_schema.statistics
                    where table_schema = database()
                      and table_name = ?
                      and index_name = ?
                    """, Integer.class, tableName, indexName);
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute(ddl);
            log.info("Created index {} on {}", indexName, tableName);
        } catch (DataAccessException ex) {
            log.warn("Skip creating index {} on {}: {}", indexName, tableName, ex.getMessage());
        }
    }

    private void backfillTaskActionScheduleAndIdempotencyFields() {
        int totalUpdated = 0;
        try {
            for (int round = 0; round < BACKFILL_MAX_ROUNDS; round++) {
                List<TaskActionBackfillRow> rows = fetchTaskActionBackfillRows();
                if (rows.isEmpty()) {
                    break;
                }
                int updated = 0;
                for (TaskActionBackfillRow row : rows) {
                    Long nextRunAtEpochMs = row.nextRunAtEpochMs() == null
                            ? toEpochMs(row.nextRunAt())
                            : row.nextRunAtEpochMs();
                    String idempotencyKey = isBlank(row.idempotencyKey())
                            ? buildIdempotencyKey(row.planId(), row.actionId(), row.triggerCount())
                            : row.idempotencyKey();
                    updated += jdbcTemplate.update("""
                            update ai_task_action
                            set next_run_at_epoch_ms = case
                                    when next_run_at_epoch_ms is null then ?
                                    else next_run_at_epoch_ms
                                end,
                                idempotency_key = case
                                    when idempotency_key is null or idempotency_key = '' then ?
                                    else idempotency_key
                                end
                            where action_id = ?
                            """, nextRunAtEpochMs, idempotencyKey, row.actionId());
                }
                totalUpdated += updated;
                if (rows.size() < BACKFILL_BATCH_SIZE) {
                    break;
                }
            }
            if (totalUpdated > 0) {
                log.info("Backfilled task action schedule/idempotency fields: count={}", totalUpdated);
            }
        } catch (DataAccessException ex) {
            log.warn("Skip backfilling task action schedule/idempotency fields: {}", ex.getMessage());
        }
    }

    private List<TaskActionBackfillRow> fetchTaskActionBackfillRows() {
        return jdbcTemplate.query("""
                select action_id,
                       plan_id,
                       next_run_at,
                       next_run_at_epoch_ms,
                       trigger_count,
                       idempotency_key
                from ai_task_action
                where (next_run_at_epoch_ms is null and next_run_at is not null and next_run_at <> '')
                   or (idempotency_key is null or idempotency_key = '')
                order by plan_id, action_id
                limit ?
                """, (rs, rowNum) -> new TaskActionBackfillRow(
                rs.getString("action_id"),
                rs.getString("plan_id"),
                rs.getString("next_run_at"),
                toLong(rs.getObject("next_run_at_epoch_ms")),
                toInteger(rs.getObject("trigger_count")),
                rs.getString("idempotency_key")
        ), BACKFILL_BATCH_SIZE);
    }

    private Long toEpochMs(String offsetDateTimeText) {
        if (isBlank(offsetDateTimeText)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(offsetDateTimeText).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String buildIdempotencyKey(String planId, String actionId, Integer triggerCount) {
        return planId + ":" + actionId + ":" + (triggerCount == null ? 0 : triggerCount);
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TaskActionBackfillRow(String actionId,
                                         String planId,
                                         String nextRunAt,
                                         Long nextRunAtEpochMs,
                                         Integer triggerCount,
                                         String idempotencyKey) {
    }
}

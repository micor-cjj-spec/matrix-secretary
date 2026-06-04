package com.kailei.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskCenterSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskCenterSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public TaskCenterSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexes();
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
}

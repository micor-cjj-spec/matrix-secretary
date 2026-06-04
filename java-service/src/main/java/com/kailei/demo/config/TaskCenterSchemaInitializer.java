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
    private static final String TABLE_NAME = "ai_task_action_execution";
    private static final String INDEX_NAME = "uk_task_action_execution_idempotency_key";

    private final JdbcTemplate jdbcTemplate;

    public TaskCenterSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureExecutionIdempotencyUniqueIndex();
    }

    private void ensureExecutionIdempotencyUniqueIndex() {
        try {
            Integer exists = jdbcTemplate.queryForObject("""
                    select count(1)
                    from information_schema.statistics
                    where table_schema = database()
                      and table_name = ?
                      and index_name = ?
                    """, Integer.class, TABLE_NAME, INDEX_NAME);
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute("alter table " + TABLE_NAME
                    + " add unique key " + INDEX_NAME + " (idempotency_key)");
            log.info("Created unique index {} on {}(idempotency_key)", INDEX_NAME, TABLE_NAME);
        } catch (DataAccessException ex) {
            log.warn("Skip creating unique index {} on {}: {}", INDEX_NAME, TABLE_NAME, ex.getMessage());
        }
    }
}

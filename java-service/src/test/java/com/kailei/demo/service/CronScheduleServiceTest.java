package com.kailei.demo.service;

import com.kailei.demo.model.TaskSchedule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CronScheduleServiceTest {

    private final CronScheduleService cronScheduleService = new CronScheduleService();

    @Test
    void shouldCalculateNextRunForFiveFieldUnixCron() {
        TaskSchedule schedule = recurring("30 9 * * *", "Asia/Shanghai");

        String nextRunAt = cronScheduleService.calculateNextRunAt(
                schedule,
                OffsetDateTime.parse("2026-06-05T09:00:00+08:00")
        );

        assertEquals("2026-06-05T09:30+08:00", nextRunAt);
    }

    @Test
    void shouldCalculateNextRunForSixFieldSpringCron() {
        TaskSchedule schedule = recurring("0 15 10 * * MON-FRI", "Asia/Shanghai");

        String nextRunAt = cronScheduleService.calculateNextRunAt(
                schedule,
                OffsetDateTime.parse("2026-06-05T10:16:00+08:00")
        );

        assertEquals("2026-06-08T10:15+08:00", nextRunAt);
    }

    @Test
    void shouldCalculateNextRunForMonthlyCron() {
        TaskSchedule schedule = recurring("0 0 9 15 * ?", "Asia/Shanghai");

        String nextRunAt = cronScheduleService.calculateNextRunAt(
                schedule,
                OffsetDateTime.parse("2026-06-05T10:00:00+08:00")
        );

        assertEquals("2026-06-15T09:00+08:00", nextRunAt);
    }

    @Test
    void shouldCalculateNextRunForSevenFieldQuartzCronWithYear() {
        TaskSchedule schedule = recurring("0 0 9 1 7 ? 2026", "Asia/Shanghai");

        String nextRunAt = cronScheduleService.calculateNextRunAt(
                schedule,
                OffsetDateTime.parse("2026-06-05T10:00:00+08:00")
        );

        assertEquals("2026-07-01T09:00+08:00", nextRunAt);
    }

    @Test
    void shouldReturnNullWhenSevenFieldYearHasPassed() {
        TaskSchedule schedule = recurring("0 0 9 1 7 ? 2026", "Asia/Shanghai");

        String nextRunAt = cronScheduleService.calculateNextRunAt(
                schedule,
                OffsetDateTime.parse("2026-07-01T09:01:00+08:00")
        );

        assertNull(nextRunAt);
    }

    @Test
    void shouldSupportQuartzNumericDayOfWeek() {
        TaskSchedule schedule = recurring("0 0 9 ? * 2 2026", "Asia/Shanghai");

        String nextRunAt = cronScheduleService.calculateNextRunAt(
                schedule,
                OffsetDateTime.parse("2026-06-05T10:00:00+08:00")
        );

        assertEquals("2026-06-08T09:00+08:00", nextRunAt);
    }

    @Test
    void shouldReturnNullForInvalidCron() {
        TaskSchedule schedule = recurring("not a cron", "Asia/Shanghai");

        String nextRunAt = cronScheduleService.calculateNextRunAt(
                schedule,
                OffsetDateTime.parse("2026-06-05T10:00:00+08:00")
        );

        assertNull(nextRunAt);
    }

    @Test
    void shouldKeepOnceScheduleRunAtAsNextRunAt() {
        TaskSchedule schedule = new TaskSchedule(
                "once",
                "明天上午九点",
                "2026-06-06T09:00:00+08:00",
                null,
                "Asia/Shanghai",
                null,
                null,
                0
        );

        TaskSchedule next = cronScheduleService.ensureCronAndNextRun(schedule);

        assertEquals("2026-06-06T09:00:00+08:00", next.nextRunAt());
        assertEquals("0 0 9 6 6 ? 2026", next.cron());
    }

    private TaskSchedule recurring(String cron, String timezone) {
        return new TaskSchedule(
                "recurring",
                null,
                null,
                cron,
                timezone,
                null,
                null,
                0
        );
    }
}

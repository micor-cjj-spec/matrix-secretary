package com.kailei.demo.service;

import com.kailei.demo.model.TaskSchedule;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

@Service
public class CronScheduleService {

    private static final Map<String, DayOfWeek> WEEKDAY_MAP = Map.of(
            "MON", DayOfWeek.MONDAY,
            "TUE", DayOfWeek.TUESDAY,
            "WED", DayOfWeek.WEDNESDAY,
            "THU", DayOfWeek.THURSDAY,
            "FRI", DayOfWeek.FRIDAY,
            "SAT", DayOfWeek.SATURDAY,
            "SUN", DayOfWeek.SUNDAY
    );

    public TaskSchedule ensureCronAndNextRun(TaskSchedule schedule) {
        if (schedule == null || !schedule.isScheduled()) {
            return schedule;
        }
        TaskSchedule withCron = schedule.cron() == null || schedule.cron().isBlank()
                ? schedule.withCron(buildCron(schedule))
                : schedule;
        if (withCron.nextRunAt() != null && !withCron.nextRunAt().isBlank()) {
            return withCron;
        }
        String nextRunAt = withCron.isOnce()
                ? withCron.runAt()
                : calculateNextRunAt(withCron, OffsetDateTime.now(resolveZone(withCron.timezone())));
        return withCron.withNextRunAt(nextRunAt);
    }

    public String buildCron(TaskSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        if (schedule.cron() != null && !schedule.cron().isBlank()) {
            return schedule.cron();
        }
        if (schedule.runAt() == null || schedule.runAt().isBlank()) {
            return null;
        }
        try {
            OffsetDateTime runAt = OffsetDateTime.parse(schedule.runAt());
            return String.format("0 %d %d %d %d ? %d",
                    runAt.getMinute(),
                    runAt.getHour(),
                    runAt.getDayOfMonth(),
                    runAt.getMonthValue(),
                    runAt.getYear());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public String calculateNextRunAt(TaskSchedule schedule, OffsetDateTime after) {
        if (schedule == null || schedule.cron() == null || schedule.cron().isBlank()) {
            return null;
        }
        String[] parts = schedule.cron().trim().split("\\s+");
        if (parts.length < 6) {
            return null;
        }
        int minute = parseInt(parts[1], 0);
        int hour = parseInt(parts[2], 9);
        String dayOfMonth = parts[3];
        String dayOfWeek = parts[5].toUpperCase(Locale.ROOT);
        ZoneId zoneId = resolveZone(schedule.timezone());
        OffsetDateTime cursor = after.withOffsetSameInstant(zoneId.getRules().getOffset(after.toInstant()))
                .withSecond(0)
                .withNano(0);

        if (WEEKDAY_MAP.containsKey(dayOfWeek)) {
            DayOfWeek targetDay = WEEKDAY_MAP.get(dayOfWeek);
            OffsetDateTime candidate = cursor.withHour(hour).withMinute(minute);
            int addDays = (targetDay.getValue() - candidate.getDayOfWeek().getValue() + 7) % 7;
            candidate = candidate.plusDays(addDays);
            if (!candidate.isAfter(cursor)) {
                candidate = candidate.plusWeeks(1);
            }
            return candidate.toString();
        }

        if ("*".equals(dayOfMonth)) {
            OffsetDateTime candidate = cursor.withHour(hour).withMinute(minute);
            if (!candidate.isAfter(cursor)) {
                candidate = candidate.plusDays(1);
            }
            return candidate.toString();
        }

        return null;
    }

    public TaskSchedule markTriggered(TaskSchedule schedule, OffsetDateTime triggeredAt) {
        if (schedule == null) {
            return null;
        }
        String triggered = triggeredAt.toString();
        String nextRunAt = schedule.isRecurring()
                ? calculateNextRunAt(schedule, triggeredAt.plusMinutes(1))
                : schedule.nextRunAt();
        return schedule.markTriggered(triggered, nextRunAt);
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
        } catch (Exception ex) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

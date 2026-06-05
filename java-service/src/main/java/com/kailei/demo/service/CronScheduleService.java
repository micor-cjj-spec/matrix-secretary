package com.kailei.demo.service;

import com.kailei.demo.model.TaskSchedule;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CronScheduleService {

    private static final int MAX_NEXT_ATTEMPTS = 512;
    private static final String[] QUARTZ_DAY_OF_WEEK_NAMES = {
            "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"
    };

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
        ParsedCron parsedCron = parseCron(schedule.cron());
        if (parsedCron == null) {
            return null;
        }

        ZoneId zoneId = resolveZone(schedule.timezone());
        ZonedDateTime cursor = after.atZoneSameInstant(zoneId).withNano(0);
        YearBounds yearBounds = resolveYearBounds(parsedCron.yearField());
        if (yearBounds.maxYear() != null && cursor.getYear() > yearBounds.maxYear()) {
            return null;
        }
        if (yearBounds.minYear() != null && cursor.getYear() < yearBounds.minYear()) {
            cursor = ZonedDateTime.of(yearBounds.minYear(), 1, 1, 0, 0, 0, 0, zoneId).minusSeconds(1);
        }

        try {
            CronExpression cronExpression = CronExpression.parse(parsedCron.expression());
            ZonedDateTime next = nextMatchingYear(cronExpression, cursor, parsedCron.yearField(), yearBounds);
            return next == null ? null : next.toOffsetDateTime().toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    private ParsedCron parseCron(String cron) {
        String normalized = cron == null ? "" : cron.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.startsWith("@")) {
            return new ParsedCron(normalized, null);
        }

        String[] parts = normalized.split("\\s+");
        if (parts.length == 5) {
            return new ParsedCron("0 " + normalized, null);
        }
        if (parts.length == 6) {
            return new ParsedCron(normalizeQuartzDayOfWeek(parts, false), null);
        }
        if (parts.length == 7) {
            String[] firstSix = new String[6];
            System.arraycopy(parts, 0, firstSix, 0, 6);
            return new ParsedCron(normalizeQuartzDayOfWeek(firstSix, true), parts[6]);
        }
        return null;
    }

    private String normalizeQuartzDayOfWeek(String[] parts, boolean hasYearField) {
        boolean quartzStyle = hasYearField || "?".equals(parts[3]) || "?".equals(parts[5]);
        if (!quartzStyle) {
            return String.join(" ", parts);
        }
        String[] copy = parts.clone();
        copy[5] = normalizeQuartzDayOfWeekField(copy[5]);
        return String.join(" ", copy);
    }

    private String normalizeQuartzDayOfWeekField(String dayOfWeek) {
        if (dayOfWeek == null || dayOfWeek.isBlank() || "*".equals(dayOfWeek) || "?".equals(dayOfWeek)) {
            return dayOfWeek;
        }
        List<String> converted = new ArrayList<>();
        for (String token : dayOfWeek.split(",")) {
            converted.add(normalizeQuartzDayOfWeekToken(token));
        }
        return String.join(",", converted);
    }

    private String normalizeQuartzDayOfWeekToken(String token) {
        String value = token.trim().toUpperCase(Locale.ROOT);
        if (value.matches("[1-7]")) {
            return quartzDayOfWeekName(Integer.parseInt(value));
        }
        if (value.matches("[1-7]-[1-7]")) {
            String[] range = value.split("-");
            return quartzDayOfWeekName(Integer.parseInt(range[0])) + "-" + quartzDayOfWeekName(Integer.parseInt(range[1]));
        }
        if (value.matches("[1-7]#[1-5]")) {
            return quartzDayOfWeekName(Integer.parseInt(value.substring(0, 1))) + value.substring(1);
        }
        return token;
    }

    private String quartzDayOfWeekName(int quartzDayOfWeek) {
        int index = quartzDayOfWeek - 1;
        return QUARTZ_DAY_OF_WEEK_NAMES[index];
    }

    private ZonedDateTime nextMatchingYear(CronExpression cronExpression,
                                           ZonedDateTime cursor,
                                           String yearField,
                                           YearBounds yearBounds) {
        for (int i = 0; i < MAX_NEXT_ATTEMPTS; i++) {
            ZonedDateTime next = cronExpression.next(cursor);
            if (next == null) {
                return null;
            }
            if (yearBounds.maxYear() != null && next.getYear() > yearBounds.maxYear()) {
                return null;
            }
            if (matchesYear(yearField, next.getYear())) {
                return next;
            }
            cursor = next;
        }
        return null;
    }

    private boolean matchesYear(String yearField, int year) {
        if (yearField == null || yearField.isBlank() || "*".equals(yearField) || "?".equals(yearField)) {
            return true;
        }
        for (String token : yearField.split(",")) {
            if (matchesYearToken(token.trim(), year)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesYearToken(String token, int year) {
        if (token.isBlank()) {
            return false;
        }
        String[] stepSplit = token.split("/", 2);
        String base = stepSplit[0];
        int step = stepSplit.length == 2 ? parseInt(stepSplit[1], 1) : 1;
        if (step <= 0) {
            step = 1;
        }
        if ("*".equals(base) || "?".equals(base)) {
            return year % step == 0;
        }
        if (base.contains("-")) {
            String[] range = base.split("-", 2);
            Integer start = parseInteger(range[0]);
            Integer end = parseInteger(range[1]);
            return start != null && end != null && year >= start && year <= end && (year - start) % step == 0;
        }
        Integer exact = parseInteger(base);
        return exact != null && year == exact;
    }

    private YearBounds resolveYearBounds(String yearField) {
        if (yearField == null || yearField.isBlank() || "*".equals(yearField) || "?".equals(yearField)) {
            return YearBounds.none();
        }
        Integer min = null;
        Integer max = null;
        for (String rawToken : yearField.split(",")) {
            String token = rawToken.trim();
            if (token.isBlank() || token.startsWith("*") || token.startsWith("?")) {
                continue;
            }
            String base = token.split("/", 2)[0];
            if (base.contains("-")) {
                String[] range = base.split("-", 2);
                Integer start = parseInteger(range[0]);
                Integer end = parseInteger(range[1]);
                if (start != null && end != null) {
                    min = min == null ? start : Math.min(min, start);
                    max = max == null ? end : Math.max(max, end);
                }
            } else {
                Integer exact = parseInteger(base);
                if (exact != null) {
                    min = min == null ? exact : Math.min(min, exact);
                    max = max == null ? exact : Math.max(max, exact);
                }
            }
        }
        return new YearBounds(min, max);
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
        } catch (Exception ex) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private int parseInt(String value, int fallback) {
        Integer parsed = parseInteger(value);
        return parsed == null ? fallback : parsed;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedCron(String expression, String yearField) {
    }

    private record YearBounds(Integer minYear, Integer maxYear) {
        private static YearBounds none() {
            return new YearBounds(null, null);
        }
    }
}

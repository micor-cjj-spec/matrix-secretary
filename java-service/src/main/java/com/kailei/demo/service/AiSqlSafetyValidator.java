package com.kailei.demo.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiSqlSafetyValidator {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+(\\d+)\\b");
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(from|join)\\s+([`\\w.]+)");

    public String validateAndNormalize(String sql, Set<String> allowedTables, Integer maxRows) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL不能为空");
        }
        String normalized = stripTrailingSemicolon(sql.trim());
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (!lower.startsWith("select ")) {
            throw new IllegalArgumentException("当前MVP只允许SELECT查询");
        }
        if (containsMultiStatementOrComment(normalized)) {
            throw new IllegalArgumentException("SQL不允许包含多语句或注释");
        }
        if (containsForbiddenKeyword(lower)) {
            throw new IllegalArgumentException("SQL包含禁止的写入/DDL/高危关键字");
        }
        if (lower.matches("(?is).*select\\s+\\*\\s+from.*")) {
            throw new IllegalArgumentException("不允许SELECT *，请明确指定字段");
        }
        validateTableWhitelist(normalized, allowedTables);
        return ensureBoundedLimit(normalized, maxRows);
    }

    private String stripTrailingSemicolon(String sql) {
        String current = sql.trim();
        while (current.endsWith(";")) {
            current = current.substring(0, current.length() - 1).trim();
        }
        return current;
    }

    private boolean containsMultiStatementOrComment(String sql) {
        return sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("*/");
    }

    private boolean containsForbiddenKeyword(String lowerSql) {
        String padded = " " + lowerSql + " ";
        String[] forbidden = {
                " insert ", " update ", " delete ", " drop ", " alter ", " truncate ",
                " create ", " replace ", " merge ", " grant ", " revoke ", " call ",
                " exec ", " execute ", " load_file ", " outfile ", " infile ", " lock "
        };
        for (String keyword : forbidden) {
            if (padded.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void validateTableWhitelist(String sql, Set<String> allowedTables) {
        if (allowedTables == null || allowedTables.isEmpty()) {
            throw new IllegalArgumentException("当前数据源还没有可查询的白名单表，请先扫描并配置元数据");
        }
        Set<String> referencedTables = extractReferencedTables(sql);
        if (referencedTables.isEmpty()) {
            throw new IllegalArgumentException("未识别到查询表");
        }
        for (String table : referencedTables) {
            if (!allowedTables.contains(table) && !allowedTables.contains(simpleName(table))) {
                throw new IllegalArgumentException("表不在AI查询白名单内: " + table);
            }
        }
    }

    private Set<String> extractReferencedTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(2);
            if (table != null && !table.isBlank()) {
                tables.add(cleanIdentifier(table));
            }
        }
        return tables;
    }

    private String cleanIdentifier(String identifier) {
        return identifier.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    private String simpleName(String table) {
        int index = table.lastIndexOf('.');
        return index >= 0 ? table.substring(index + 1) : table;
    }

    private String ensureBoundedLimit(String sql, Integer requestedMaxRows) {
        int maxRows = requestedMaxRows == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedMaxRows, MAX_LIMIT));
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql + " LIMIT " + maxRows;
        }
        int existingLimit = Integer.parseInt(matcher.group(1));
        if (existingLimit <= maxRows) {
            return sql;
        }
        return matcher.replaceFirst("LIMIT " + maxRows);
    }
}

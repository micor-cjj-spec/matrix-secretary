package com.kailei.demo.service;

import com.kailei.demo.entity.AiSchemaColumnEntity;
import com.kailei.demo.entity.AiSchemaTableEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class AiRuleBasedSqlGenerator {

    private static final int DEFAULT_LIMIT = 100;

    public GeneratedSql generate(String question,
                                 List<AiSchemaTableEntity> tables,
                                 List<AiSchemaColumnEntity> columns,
                                 Integer maxRows) {
        AiSchemaTableEntity table = matchTable(question, tables)
                .orElseThrow(() -> new IllegalArgumentException("暂未识别到要查询的表，请在问题中包含表名或业务名称"));
        List<String> selectedColumns = columns.stream()
                .filter(column -> table.getTableName().equals(column.getTableName()))
                .filter(column -> Integer.valueOf(1).equals(column.getQueryEnabledFlag()))
                .limit(12)
                .map(AiSchemaColumnEntity::getColumnName)
                .toList();
        if (selectedColumns.isEmpty()) {
            throw new IllegalArgumentException("表没有可查询字段: " + table.getTableName());
        }
        boolean countIntent = containsAny(question, "多少", "数量", "总数", "count", "几条");
        String sql;
        if (countIntent) {
            sql = "SELECT COUNT(1) AS total_count FROM " + quote(table.getTableName());
        } else {
            int limit = maxRows == null ? DEFAULT_LIMIT : Math.max(1, Math.min(maxRows, DEFAULT_LIMIT));
            sql = "SELECT " + selectedColumns.stream().map(this::quote).reduce((a, b) -> a + ", " + b).orElseThrow()
                    + " FROM " + quote(table.getTableName()) + " LIMIT " + limit;
        }
        return new GeneratedSql(sql, table.getTableName(), countIntent ? "count" : "list");
    }

    private Optional<AiSchemaTableEntity> matchTable(String question, List<AiSchemaTableEntity> tables) {
        String normalizedQuestion = normalize(question);
        return tables.stream()
                .filter(table -> Integer.valueOf(1).equals(table.getQueryEnabledFlag()))
                .filter(table -> normalizedQuestion.contains(normalize(table.getTableName()))
                        || normalizedQuestion.contains(normalize(nullToBlank(table.getBusinessName())))
                        || normalizedQuestion.contains(normalize(nullToBlank(table.getTableComment()))))
                .findFirst();
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return nullToBlank(text).toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private String nullToBlank(String text) {
        return text == null ? "" : text;
    }

    private String quote(String identifier) {
        return "`" + identifier.replace("`", "") + "`";
    }

    public record GeneratedSql(String sql, String tableName, String intent) {
    }
}

package com.kailei.demo.service;

import com.kailei.demo.entity.AiDataSourceConfigEntity;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiReadOnlySqlExecutor {

    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int MAX_ROWS = 500;

    public QueryResult query(AiDataSourceConfigEntity dataSource, String sql) {
        try (Connection connection = DriverManager.getConnection(
                dataSource.getJdbcUrl(), dataSource.getUsername(), dataSource.getPassword())) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(MAX_ROWS);
                try (ResultSet resultSet = statement.executeQuery(sql)) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String label = metaData.getColumnLabel(i);
                        columns.add(label == null || label.isBlank() ? metaData.getColumnName(i) : label);
                    }
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (resultSet.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(columns.get(i - 1), resultSet.getObject(i));
                        }
                        rows.add(row);
                    }
                    return new QueryResult(columns, rows);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("执行只读SQL失败: " + ex.getMessage(), ex);
        }
    }

    public record QueryResult(List<String> columns, List<Map<String, Object>> rows) {
    }
}

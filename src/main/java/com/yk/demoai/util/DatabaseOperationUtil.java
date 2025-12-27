package com.yk.demoai.util;

import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseOperationUtil{
    /**
     * 连接数据库并提取表结构描述
     */
    public static String extractSchema(String url, String user, String password) {
        // 1. 动态构建数据源 (不用在 application.yml 里配死)
        DataSource dataSource = DataSourceBuilder.create()
                .url(url)
                .username(user)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();

        StringBuilder schemaBuilder = new StringBuilder();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // 2. 获取所有表名
            // 参数: catalog, schemaPattern, tableNamePattern, types
            ResultSet tables = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                schemaBuilder.append("Table: ").append(tableName).append(" (\n");

                // 3. 获取该表的所有列
                ResultSet columns = metaData.getColumns(conn.getCatalog(), null, tableName, "%");
                while (columns.next()) {
                    String colName = columns.getString("COLUMN_NAME");
                    String colType = columns.getString("TYPE_NAME");
                    String remarks = columns.getString("REMARKS"); // 字段注释，对 AI 很有用！

                    schemaBuilder.append("  - ").append(colName).append(" ").append(colType);
                    if (remarks != null && !remarks.isEmpty()) {
                        schemaBuilder.append(" // ").append(remarks);
                    }
                    schemaBuilder.append("\n");
                }
                schemaBuilder.append(")\n\n");
            }

        } catch (Exception e) {
            throw new RuntimeException("读取数据库 Schema 失败: " + e.getMessage(), e);
        }

        return schemaBuilder.toString();
    }

    /**
     * 执行 SQL 并返回结果集
     */
    public static List<Map<String, Object>> executeQuery(String sql, String url, String username, String password) {

        // 这是一个简单粗暴的检查，防止执行非查询语句
        String upperSql = sql.trim().toUpperCase();
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("SHOW") && !upperSql.startsWith("DESC")) {
            throw new IllegalArgumentException("安全警告：本工具仅支持查询操作 (SELECT/SHOW)，禁止执行修改或删除！");
        }

        // 1. 动态创建连接
        DataSource dataSource = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();

        List<Map<String, Object>> resultList = new ArrayList<>();

        // 2. 原生 JDBC 执行
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // 获取元数据（列名）
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // 3. 遍历结果集，封装成 List<Map>
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i); // 获取别名或列名
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                resultList.add(row);
            }

        } catch (SQLException e) {
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        }

        return resultList;
    }
}

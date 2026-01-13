package com.yk.demoai.util;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseOperationUtil {

    // 1. 【性能优化】引入缓存池
    // Key: url + user + password (MD5或直接拼接)
    // Value: DataSource 实例
    private static final Map<String, DataSource> DATA_SOURCE_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取或创建数据源 (实现了缓存机制)
     */
    private static DataSource getDataSource(String url, String username, String password) {
        // 构建缓存 Key (包含密码是为了防止同一用户修改密码后，缓存里的旧连接池失效)
        String cacheKey = url + "||" + username + "||" + password;

        // 2. 打印缓存池大小，看看有没有涨
        // computeIfAbsent: 这是一个原子操作，线程安全。如果 Key 不存在，才执行后面的创建逻辑
        return DATA_SOURCE_CACHE.computeIfAbsent(cacheKey, k -> {
            // 显式使用 HikariDataSource，方便进行精细化配置
            HikariDataSource ds = DataSourceBuilder.create()
                    .url(url)
                    .username(username)
                    .password(password)
                    .type(HikariDataSource.class) // 指定使用 HikariCP 高性能连接池
                    .driverClassName("com.mysql.cj.jdbc.Driver")
                    .build();

            // 动态连接场景的优化
            ds.setMaximumPoolSize(5);      // 限制每个库的最大连接数，防止占满资源
            ds.setMinimumIdle(1);          // 最小空闲连接
            ds.setIdleTimeout(60000);      // 空闲 60 秒后释放连接
            ds.setConnectionTimeout(5000); // 连接超时时间 5 秒
            ds.setPoolName("AI-SQL-Pool-" + Math.abs(k.hashCode()));

            return ds;
        });
    }

    /**
     * 连接数据库并提取表结构描述
     */
    public static String extractSchema(String url, String user, String password) {
        StringBuilder schemaBuilder = new StringBuilder();

        // 使用缓存获取数据源
        DataSource dataSource = getDataSource(url, user, password);

        try (Connection conn = dataSource.getConnection()) {
            // 尝试设置为只读模式 (这是一个 Hint，取决于驱动是否支持，但有比没有好)
            conn.setReadOnly(true);

            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                schemaBuilder.append("Table: ").append(tableName).append(" (\n");

                ResultSet columns = metaData.getColumns(conn.getCatalog(), null, tableName, "%");
                while (columns.next()) {
                    String colName = columns.getString("COLUMN_NAME");
                    String colType = columns.getString("TYPE_NAME");
                    String remarks = columns.getString("REMARKS");

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
        // 第一层防线：关键字检查
        String upperSql = sql.trim().toUpperCase();
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("SHOW") && !upperSql.startsWith("DESC")) {
            throw new IllegalArgumentException("安全警告：禁止执行非查询语句！");
        }

        // 使用缓存获取数据源
        DataSource dataSource = getDataSource(url, username, password);

        List<Map<String, Object>> resultList = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 【安全优化】第二层防线：JDBC 层面设置只读
            // 注意：这不能替代数据库账号的权限管理，但能防止部分意外写入
            conn.setReadOnly(true);

//            // 限制最大查询行数，防止内存溢出 (OOM)
//            stmt.setMaxRows(100);

            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    resultList.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        }

        return resultList;
    }
}
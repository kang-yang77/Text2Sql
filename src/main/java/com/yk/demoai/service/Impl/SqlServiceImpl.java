package com.yk.demoai.service.Impl;
import com.yk.demoai.dto.SqlRequest;
import com.yk.demoai.service.ISqlService;
import com.yk.demoai.service.SqlGenerator;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.io.Closeable;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static com.yk.demoai.util.DatabaseOperationUtil.executeQuery;
import static com.yk.demoai.util.DatabaseOperationUtil.extractSchema;

@Slf4j
@Service
public class SqlServiceImpl implements ISqlService {
    private static final Pattern READ_ONLY_PATTERN = Pattern.compile(
            "^\\s*(SELECT|SHOW|DESC|DESCRIBE|EXPLAIN|WITH)\\s+.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    @Resource
    private ContentRetriever contentRetriever;
    @Resource
    private SqlGenerator sqlGenerator;
    @Override
    public Map<String, Object> generateSql(SqlRequest request) {
        log.info("V3请求: 用户提问 [{}]", request.getQuestion());

        // 1. 抓取表结构
        String realSchema = extractSchema(
                request.getDbUrl(), request.getUsername(), request.getPassword()
        );

        // 2.使用用户的提问去向量库里找相关的知识
        List<Content> contents = contentRetriever.retrieve(new Query(request.getQuestion()));

        String ragContext = contents.stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n---\n"));

        if (ragContext.isEmpty()) {
            ragContext = "无相关业务背景知识";
        }

        log.info("RAG 检索到的上下文: \n{}", ragContext);

        // 3. AI 生成 SQL
        String rawSql = sqlGenerator.generate(realSchema, "MySQL", ragContext,request.getQuestion());

        // 4. 清洗 SQL (去掉 markdown 和换行)
        String cleanSql = rawSql.replace("\n", " ").trim()
                .replace("```sql", "")
                .replace("```", "");
        // 移除可能存在的末尾分号 (有些 JDBC 驱动不喜欢分号)
        if (cleanSql.endsWith(";")) {
            cleanSql = cleanSql.substring(0, cleanSql.length() - 1);
        }

        log.info("生成的 SQL: {}", cleanSql);

        // 5. 执行 SQL 并获取数据
        List<Map<String, Object>> queryResult = executeQuery(
                cleanSql,
                request.getDbUrl(),
                request.getUsername(),
                request.getPassword()
        );

        // 6. 返回全套结果
        return Map.of(
                "sql", cleanSql,
                "data", queryResult,
                "count", queryResult.size() // 方便前端显示条数
        );
    }

    @Override
    public Map<String, Object> executeSql(SqlRequest request) {
        String sql = request.getSql();
        String url = request.getDbUrl();
        String username = request.getUsername();
        String password = request.getPassword();

        // 1. 基础校验
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        // 2. 安全检查：只读锁
        if (!isReadOnly(sql)) {
            log.warn("拦截到潜在危险 SQL: {}", sql);
            throw new IllegalArgumentException("安全拦截：本工具仅支持查询操作 (SELECT/SHOW/WITH)，禁止增删改！");
        }

        log.info("开始执行 SQL: {}", sql);

        // 3. 动态创建 DataSource (连接池)
        // 显式指定为 HikariDataSource，以便后续强制关闭
        DataSource dataSource = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .type(HikariDataSource.class)
                .build();

        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            // 4. 获取连接并执行
            try (Connection conn = dataSource.getConnection()) {
                // JDBC 层面的只读双重保险
                conn.setReadOnly(true);

                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(15); // 设置超时防止死循环

                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> row = new HashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                String key = metaData.getColumnLabel(i);
                                Object val = rs.getObject(i);
                                row.put(key, val);
                            }
                            resultList.add(row);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("SQL 执行异常", e);
            if (e.getMessage() != null && e.getMessage().contains("Access denied")) {
                throw new RuntimeException("数据库连接失败：用户名或密码错误。");
            }
            throw new RuntimeException("SQL 执行错误: " + e.getMessage());
        } finally {
            // 防止 HikariCP 线程泄漏导致的 Thread starvation 报错
            closeDataSource(dataSource);
        }

        // 6. 组装返回结果
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("data", resultList);
        resultMap.put("count", resultList.size());

        return resultMap;
    }

    @Override
    public Map<String, String> testConnection(SqlRequest request) {
        log.info("收到连接测试请求: {}", request.getDbUrl());
        try {
            // 如果能成功提取出 Schema，说明连接和权限都由没问题
            extractSchema(request.getDbUrl(), request.getUsername(), request.getPassword());
            return Map.of("status", "success", "message", "连接成功！");
        } catch (Exception e) {
            log.error("连接测试失败", e);
            throw new IllegalArgumentException("连接失败: " + e.getMessage());
        }
    }

    private void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            log.debug("Hikari 连接池已关闭");
        } else if (dataSource instanceof Closeable) {
            try {
                ((Closeable) dataSource).close();
            } catch (Exception e) {
                log.warn("关闭 DataSource 失败", e);
            }
        }
    }

    private boolean isReadOnly(String sql) {
        if (sql == null || sql.trim().isEmpty()) return false;
        return READ_ONLY_PATTERN.matcher(sql.trim()).matches();
    }
}

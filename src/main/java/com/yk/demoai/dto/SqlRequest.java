package com.yk.demoai.dto;
import lombok.Data;

@Data
public class SqlRequest {
    private String tableSchema;

    // 用户的自然语言问题
    private String question;

    private String sql;

    private String dbUrl;

    private String username;

    private String password;

    // 可选：指定只想查哪几张表，如果为空则查全库
    private String[] tableNames;

}

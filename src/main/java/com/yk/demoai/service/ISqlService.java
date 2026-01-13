package com.yk.demoai.service;

import com.yk.demoai.dto.Result;
import com.yk.demoai.dto.SqlRequest;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

public interface ISqlService {
    public Map<String, Object> generateSql(SqlRequest request);
    public Map<String, Object> executeSql(SqlRequest request);
    public Map<String, String> testConnection(SqlRequest request);
}

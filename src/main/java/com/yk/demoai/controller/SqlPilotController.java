package com.yk.demoai.controller;
import com.yk.demoai.dto.SqlRequest;
import com.yk.demoai.service.ISqlService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;



@Slf4j
@RestController
@RequestMapping("/api/sql")
public class SqlPilotController {
    @Resource
    private ISqlService sqlService;


    @PostMapping("/generate")
    public Map<String, Object> generateSql(@RequestBody SqlRequest request) {
        return sqlService.generateSql(request);
    }

    @PostMapping("/execute")
    public Map<String, Object> executeSql(@RequestBody SqlRequest request) {
        return sqlService.executeSql(request);
    }
    @PostMapping("/test-connection")
    public Map<String, String> testConnection(@RequestBody SqlRequest request) {
        return sqlService.testConnection(request);
    }
}

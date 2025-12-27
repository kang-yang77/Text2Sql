package com.yk.demoai.configure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        // 打印堆栈信息到控制台，方便开发者排查
        e.printStackTrace();

        // 获取异常的具体消息
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            message = "服务器内部错误";
        }

        // 返回 400 Bad Request 状态码，并携带 error 字段
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}

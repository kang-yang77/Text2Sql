package com.yk.demoai.dto;
import java.util.Map;

public class Result {
    private String result;

    private Map<String, Object> data;

    private Integer count;

    private String message;

    public Result(String result, Map<String, Object> data, Integer count, String message) {
        this.result = result;
        this.data = data;
        this.count = count;
        this.message = message;
    }

    public static Result success(Map<String, Object> data) {
        return new Result("success", data, data == null ? 0 : data.size(), null);
    }

    public static Result fail(String errorMessage) {
        // 失败时：result="fail", data=null, count=0, message=错误详情
        return new Result("fail", null, 0, errorMessage);
    }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

package com.yk.demoai.service;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SqlGenerator {
    @SystemMessage(fromResource = "sql_prompt.md")
    String generate(@V("schema") String schema,
                    @V("dbType") String dbType,
                    @V("ragContext") String ragContext,
                    @UserMessage String question);
}

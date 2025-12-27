package com.yk.demoai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DemoAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoAiApplication.class, args);
    }

}

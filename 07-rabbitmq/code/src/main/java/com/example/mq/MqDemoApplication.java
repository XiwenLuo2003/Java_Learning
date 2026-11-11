package com.example.mq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MqDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqDemoApplication.class, args);
    }
}

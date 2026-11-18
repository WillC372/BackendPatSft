package com.aquasense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AquaAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AquaAssistantApplication.class, args);
    }
}

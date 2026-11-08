package com.aquasense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AquaSenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AquaSenseApplication.class, args);
    }
}

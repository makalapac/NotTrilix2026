package com.nottrilix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NotTrilixApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotTrilixApplication.class, args);
    }
}

package com.nottrilix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // Faza 5: analiza utihnulih razgovora i retencija podataka po rasporedu
public class NotTrilixApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotTrilixApplication.class, args);
    }
}

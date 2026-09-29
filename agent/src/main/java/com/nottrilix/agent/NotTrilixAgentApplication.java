package com.nottrilix.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agent podrske kao zasebna aplikacija. Nema bazu ni poslovna pravila: sve sto zna i moze dolazi iz sustava
 * preko MCP-a (alati i baza znanja), a razgovor vodi Spring AI ChatClient.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotTrilixAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotTrilixAgentApplication.class, args);
    }
}

package com.nottrilix.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Support agent as a separate application. It has no database and no business rules: everything it knows and can do
 * comes from the system over MCP (tools and knowledge base), and the conversation is driven by Spring AI ChatClient.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotTrilixAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotTrilixAgentApplication.class, args);
    }
}

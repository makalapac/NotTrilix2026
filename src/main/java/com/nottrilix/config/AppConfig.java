package com.nottrilix.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /** Jedan izvor vremena za servise (testovi ubace fiksni Clock). */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Zagreb"));
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("NotTrilix 2026 API")
                .version("0.1.0")
                .description("Prodaja bonova (A1, HT, Steam, PlayStation, Xbox). "
                        + "Jedan API za prodajni kanal (shop) i za admin/support."));
    }
}

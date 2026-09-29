package com.nottrilix.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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

    /** Swagger: gumb "Authorize" prima bearer token dobiven na /api/auth/.../login. */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NotTrilix 2026 API")
                        .version("0.4.0")
                        .description("Prodaja bonova (A1, HT, Steam, PlayStation, Xbox). "
                                + "Jedan API za prodajni kanal (shop, uloga CUSTOMER) i za admin/support (uloga ADMIN). "
                                + "Prijava: POST /api/auth/customer/login ili /api/auth/admin/login -> bearer token. "
                                + "Za agente i druge MCP klijente isti servisi su dostupni kao MCP server: POST /mcp."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}

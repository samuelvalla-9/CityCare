package com.citycare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================
 * SwaggerConfig.java  –  Interactive API Documentation Setup
 * ============================================================
 *
 * After starting the app, visit:
 *   http://localhost:8080/api/swagger-ui.html
 *
 * HOW TO USE SWAGGER UI FOR TESTING:
 *   Step 1: Expand POST /auth/login  →  Try it out
 *   Step 2: Enter email + password → Execute
 *   Step 3: Copy the "token" from the response
 *   Step 4: Click the "Authorize" button (top right of Swagger UI)
 *   Step 5: Type:  Bearer <paste-token-here>  → click Authorize
 *   Step 6: All subsequent calls automatically include the token
 *
 * The "bearerAuth" scheme adds an "Authorize" button to Swagger UI
 * where you paste your JWT. Swagger then adds:
 *   Authorization: Bearer <token>
 * to every test request automatically.
 *
 * ============================================================
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CityCare API")
                        .description("""
                                Urban Healthcare & Emergency Medical Response System
                                
                                WORKFLOW:
                                1. Citizen registers → reports emergency
                                2. Dispatcher views pending → assigns available ambulance
                                3. Admin views dispatched → admits patient (ambulance auto-released)
                                4. Doctor/Nurse assigns treatments → updates patient status
                                """)
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste JWT token (without 'Bearer' prefix). Get it from POST /auth/login")));
    }
}

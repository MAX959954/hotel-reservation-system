package com.folio.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API docs served by springdoc at /swagger-ui.html (spec at /v3/api-docs). Disabled in
 *  the prod profile — see application-prod.yml. The bearer scheme makes Swagger UI's
 *  "Authorize" button send an access token from /api/auth/otp/verify or /api/auth/refresh
 *  as "Authorization: Bearer ...", the same header JwtAuthFilter reads. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI folioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Folio API")
                        .version("v1")
                        .description("REST API of the Folio hotel booking platform. Most endpoints need a JWT: "
                                + "sign in through /api/auth/login + /api/auth/otp/verify, then click Authorize "
                                + "and paste auth.token from the response."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}

package com.jobseekercopilot.stripegateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI stripeGatewayOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "serviceToken",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Service-Token")
                                .description("Dedicated Payment Gateway service identity.")))
                .info(new Info()
                        .title("Stripe Gateway API")
                        .description("Fail-closed provider boundary for server-owned checkout creation, signed "
                                + "Stripe webhooks, readiness and owner-scoped Checkout Session expiry.")
                        .version("2.1.0")
                        .contact(new Contact().name("Jobseeker Copilot"))
                        .license(new License().name("MIT")));
    }
}

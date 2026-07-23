package com.jobseekercopilot.stripegateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI stripeGatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stripe Gateway API")
                        .description("Gateway for Stripe Checkout session creation and signed Stripe webhooks.")
                        .version("1.0.0")
                        .contact(new Contact().name("Jobseeker Copilot"))
                        .license(new License().name("MIT")));
    }
}

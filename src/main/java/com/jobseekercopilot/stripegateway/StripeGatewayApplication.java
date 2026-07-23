package com.jobseekercopilot.stripegateway;

import com.jobseekercopilot.stripegateway.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StripeProperties.class)
public class StripeGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(StripeGatewayApplication.class, args);
    }
}

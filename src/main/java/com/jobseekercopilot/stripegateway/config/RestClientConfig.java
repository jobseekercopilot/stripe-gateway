package com.jobseekercopilot.stripegateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    RestClient stripeRestClient(StripeProperties stripeProperties) {
        return RestClient.builder()
                .baseUrl(stripeProperties.getApiBaseUrl())
                .build();
    }

    @Bean
    RestClient paymentServiceRestClient(@Value("${services.payment-service.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}

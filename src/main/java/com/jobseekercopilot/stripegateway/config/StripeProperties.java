package com.jobseekercopilot.stripegateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
@Data
public class StripeProperties {
    private String apiBaseUrl;
    private String secretKey;
    private String webhookSecret;
    private String apiVersion;
    private boolean liveReleaseAuthorised;
    private boolean legacyCheckoutEnabled;
    private String successUrl;
    private String cancelUrl;
}

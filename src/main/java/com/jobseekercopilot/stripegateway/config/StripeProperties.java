package com.jobseekercopilot.stripegateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

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
    private String starterPriceId;
    private String activePriceId;
    private String powerPriceId;

    public String priceIdFor(String pricingPlanId) {
        if (pricingPlanId == null) {
            return null;
        }
        return switch (pricingPlanId.toLowerCase(Locale.ROOT)) {
            case "starter" -> starterPriceId;
            case "active" -> activePriceId;
            case "power" -> powerPriceId;
            default -> null;
        };
    }
}

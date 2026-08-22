package com.jobseekercopilot.stripegateway.service;

import com.jobseekercopilot.stripegateway.config.ExternalProviderMode;
import com.jobseekercopilot.stripegateway.config.ExternalProviderProperties;
import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.StripeReadinessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StripeProviderReadiness {
    private final ExternalProviderProperties providerProperties;
    private final StripeProperties stripeProperties;

    public StripeReadinessResponse readiness() {
        ExternalProviderMode mode = providerProperties.getMode();
        boolean catalogConfigured = configuredPrice(stripeProperties.getStarterPriceId())
                && configuredPrice(stripeProperties.getActivePriceId())
                && configuredPrice(stripeProperties.getPowerPriceId());
        boolean available = mode == ExternalProviderMode.FIXTURE
                || mode == ExternalProviderMode.LIVE
                && stripeProperties.isLiveReleaseAuthorised()
                && catalogConfigured;
        String code = switch (mode) {
            case DISABLED -> "PAYMENTS_DISABLED";
            case FIXTURE -> "READY";
            case LIVE -> !stripeProperties.isLiveReleaseAuthorised()
                    ? "LIVE_RELEASE_NOT_AUTHORISED"
                    : catalogConfigured ? "READY" : "STRIPE_CATALOG_NOT_CONFIGURED";
        };
        return StripeReadinessResponse.builder()
                .checkoutAvailable(available)
                .code(code)
                .mode(mode.name())
                .build();
    }

    private boolean configuredPrice(String value) {
        return value != null && value.matches("price_[A-Za-z0-9]+");
    }
}

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
        boolean available = mode == ExternalProviderMode.FIXTURE
                || mode == ExternalProviderMode.LIVE
                && stripeProperties.isLiveReleaseAuthorised();
        String code = switch (mode) {
            case DISABLED -> "PAYMENTS_DISABLED";
            case FIXTURE -> "READY";
            case LIVE -> stripeProperties.isLiveReleaseAuthorised()
                    ? "READY" : "LIVE_RELEASE_NOT_AUTHORISED";
        };
        return StripeReadinessResponse.builder()
                .checkoutAvailable(available)
                .code(code)
                .mode(mode.name())
                .build();
    }
}

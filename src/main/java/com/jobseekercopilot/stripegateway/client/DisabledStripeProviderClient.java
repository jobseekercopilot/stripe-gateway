package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external-provider", name = "mode", havingValue = "DISABLED", matchIfMissing = true)
public class DisabledStripeProviderClient implements StripeProviderClient {
    @Override
    public StripeCheckoutSession createCheckoutSession(
            CreateCheckoutSessionRequest request, String productName) {
        throw disabled();
    }

    @Override
    public StripeCheckoutSession createOwnedCheckoutSession(PaymentOrderSnapshot order) {
        throw disabled();
    }

    @Override
    public StripeCheckoutSession retrieveOwnedCheckoutSession(String sessionId) {
        throw disabled();
    }

    @Override
    public StripeCheckoutSession expireOwnedCheckoutSession(String sessionId) {
        throw disabled();
    }

    private StripeConfigurationException disabled() {
        return new StripeConfigurationException("Stripe Checkout is disabled for this release");
    }
}

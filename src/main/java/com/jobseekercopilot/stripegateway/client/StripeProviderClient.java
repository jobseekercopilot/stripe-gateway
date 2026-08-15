package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;

public interface StripeProviderClient {
    StripeCheckoutSession createCheckoutSession(CreateCheckoutSessionRequest request, String productName);

    StripeCheckoutSession createOwnedCheckoutSession(PaymentOrderSnapshot order);
    StripeCheckoutSession retrieveOwnedCheckoutSession(String sessionId);
    StripeCheckoutSession expireOwnedCheckoutSession(String sessionId);
}

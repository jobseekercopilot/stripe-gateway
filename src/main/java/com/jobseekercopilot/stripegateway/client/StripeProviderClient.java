package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;

public interface StripeProviderClient {
    StripeCheckoutSession createCheckoutSession(CreateCheckoutSessionRequest request, String productName);
}

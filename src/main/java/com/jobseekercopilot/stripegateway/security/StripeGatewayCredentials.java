package com.jobseekercopilot.stripegateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class StripeGatewayCredentials {
    static final int MINIMUM_TOKEN_BYTES = 32;

    private final String paymentGatewayToken;
    private final String paymentServiceToken;
    private final String paymentLifecycleToken;

    @Autowired
    public StripeGatewayCredentials(
            @Value("${stripe.security.payment-gateway-token}") String paymentGatewayToken,
            @Value("${stripe.security.payment-service-token}") String paymentServiceToken,
            @Value("${stripe.security.payment-lifecycle-token}") String paymentLifecycleToken) {
        this.paymentGatewayToken = validate(paymentGatewayToken, "Payment Gateway service token");
        this.paymentServiceToken = validate(paymentServiceToken, "Payment Service token");
        this.paymentLifecycleToken = validate(
                paymentLifecycleToken, "Payment lifecycle service token");
        if (matches(this.paymentGatewayToken, this.paymentServiceToken)) {
            throw new IllegalStateException("Stripe Gateway service identity tokens must be distinct.");
        }
        if (matches(this.paymentGatewayToken, this.paymentLifecycleToken)
                || matches(this.paymentServiceToken, this.paymentLifecycleToken)) {
            throw new IllegalStateException("Stripe Gateway service identity tokens must be distinct.");
        }
    }

    public StripeGatewayCredentials(String paymentGatewayToken, String paymentServiceToken) {
        this(paymentGatewayToken, paymentServiceToken,
                "account-lifecycle-stripe-test-token-000000001");
    }

    public boolean authenticatesPaymentGateway(String supplied) {
        return matches(supplied, paymentGatewayToken);
    }

    public String paymentServiceToken() {
        return paymentServiceToken;
    }

    public boolean authenticatesPaymentLifecycle(String supplied) {
        return matches(supplied, paymentLifecycleToken);
    }

    private static String validate(String value, String label) {
        if (value == null
                || value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length < MINIMUM_TOKEN_BYTES) {
            throw new IllegalStateException(label + " must contain at least 32 bytes.");
        }
        return value;
    }

    private static boolean matches(String supplied, String expected) {
        return supplied != null && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}

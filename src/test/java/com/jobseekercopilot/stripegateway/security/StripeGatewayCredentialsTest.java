package com.jobseekercopilot.stripegateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeGatewayCredentialsTest {
    private static final String GATEWAY = "payment-gateway-stripe-token-00000000000001";
    private static final String SERVICE = "stripe-payment-service-token-00000000000001";

    @Test
    void authenticatesOnlyTheConfiguredPaymentGateway() {
        StripeGatewayCredentials credentials = new StripeGatewayCredentials(GATEWAY, SERVICE);

        assertThat(credentials.authenticatesPaymentGateway(GATEWAY)).isTrue();
        assertThat(credentials.authenticatesPaymentGateway("forged")).isFalse();
        assertThat(credentials.authenticatesPaymentGateway(null)).isFalse();
        assertThat(credentials.paymentServiceToken()).isEqualTo(SERVICE);
    }

    @Test
    void rejectsMissingShortOrReusedTokens() {
        assertThatThrownBy(() -> new StripeGatewayCredentials("", SERVICE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment Gateway");
        assertThatThrownBy(() -> new StripeGatewayCredentials("short", SERVICE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new StripeGatewayCredentials(GATEWAY, GATEWAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe Gateway service identity tokens must be distinct.");
    }
}

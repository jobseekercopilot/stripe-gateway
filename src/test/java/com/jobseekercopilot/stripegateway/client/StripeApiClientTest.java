package com.jobseekercopilot.stripegateway.client;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class StripeApiClientTest {
    @Test
    void ownedCheckoutPassesTheDurableOrderExpiryWithMoreThanStripeMinimumRemaining() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.stripe.test");
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        StripeProperties properties = new StripeProperties();
        properties.setSecretKey("sk_test_owned_checkout");
        properties.setApiVersion("2025-06-30.basil");
        properties.setSuccessUrl("https://app.example.test/payment/success");
        properties.setCancelUrl("https://app.example.test/payment/cancel");
        StripeApiClient client = new StripeApiClient(builder.build(), properties);

        PaymentOrderSnapshot order = new PaymentOrderSnapshot();
        order.setOrderId(UUID.fromString(
                "1c05d1ab-e57b-4904-b627-e55a7132207c"));
        order.setStatus("PENDING_CHECKOUT");
        order.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        order.setCurrency("GBP");
        order.setPriceMinor(1699);
        order.setPricingPlanName("Active");
        order.setCatalogVersion("public-beta-2026-08-15");
        long remainingSeconds = Duration.between(
                Instant.now(), order.getExpiresAt()).toSeconds();
        org.assertj.core.api.Assertions.assertThat(remainingSeconds)
                .isGreaterThan(30 * 60);

        server.expect(requestTo("https://api.stripe.test/v1/checkout/sessions"))
                .andExpect(header("Stripe-Version", "2025-06-30.basil"))
                .andExpect(header(
                        "Idempotency-Key",
                        "checkout:" + order.getOrderId()))
                .andExpect(content().string(containsString(
                        "expires_at=" + order.getExpiresAt().getEpochSecond())))
                .andRespond(withSuccess(
                        "{\"id\":\"cs_test_owned\",\"status\":\"open\"}",
                        MediaType.APPLICATION_JSON));

        client.createOwnedCheckoutSession(order);

        server.verify();
    }
}

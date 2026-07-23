package com.jobseekercopilot.stripegateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.stripegateway.client.PaymentServiceClient;
import com.jobseekercopilot.stripegateway.client.StripeApiClient;
import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeGatewayServiceTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000);

    @Test
    void checkoutSessionRequestCreatesStripeSession() {
        StripeApiClient stripeApiClient = mock(StripeApiClient.class);
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        StripeGatewayService service = new StripeGatewayService(
                stripeApiClient,
                verifier(),
                paymentServiceClient);
        CreateCheckoutSessionRequest request = checkoutRequest();
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId("cs_test_123");
        session.setUrl("https://checkout.stripe.com/c/pay/cs_test_123");
        when(stripeApiClient.createCheckoutSession(eq(request), eq("Job Seeker Copilot AI Tokens - Starter")))
                .thenReturn(session);

        CreateCheckoutSessionResponse response = service.createCheckoutSession(request);

        assertThat(response.getSessionId()).isEqualTo("cs_test_123");
        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_123");
    }

    @Test
    void checkoutSessionCompletedWebhookCallsPaymentService() throws Exception {
        StripeApiClient stripeApiClient = mock(StripeApiClient.class);
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        StripeGatewayService service = new StripeGatewayService(stripeApiClient, verifier(), paymentServiceClient);
        String payload = """
                {
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "payment_intent": "pi_test_123",
                      "metadata": {
                        "userId": "user-123",
                        "pricingPlanId": "starter",
                        "tokenAmount": "100000"
                      }
                    }
                  }
                }
                """;

        service.handleWebhook(payload, signedHeader(payload));

        ArgumentCaptor<ConfirmStripePurchaseRequest> captor =
                ArgumentCaptor.forClass(ConfirmStripePurchaseRequest.class);
        verify(paymentServiceClient).confirmStripePurchase(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-123");
        assertThat(captor.getValue().getPricingPlanId()).isEqualTo("starter");
        assertThat(captor.getValue().getTokenAmount()).isEqualTo(100000);
        assertThat(captor.getValue().getStripeSessionId()).isEqualTo("cs_test_123");
        assertThat(captor.getValue().getStripePaymentIntentId()).isEqualTo("pi_test_123");
    }

    @Test
    void webhookVerifierAcceptsValidStripeSignature() {
        StripeWebhookVerifier verifier = verifier();
        String payload = "{\"type\":\"checkout.session.completed\"}";

        assertThat(verifier.verifyAndParse(payload, signedHeader(payload)).path("type").asText())
                .isEqualTo("checkout.session.completed");
    }

    private CreateCheckoutSessionRequest checkoutRequest() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setUserId("user-123");
        request.setPricingPlanId("starter");
        request.setTokenAmount(100000);
        request.setPriceGbpPence(799);
        return request;
    }

    private StripeWebhookVerifier verifier() {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret("whsec_test_secret");
        StripeWebhookVerifier verifier = new StripeWebhookVerifier(properties, new ObjectMapper());
        verifier.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        return verifier;
    }

    private String signedHeader(String payload) {
        long timestamp = NOW.getEpochSecond();
        return "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("whsec_test_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

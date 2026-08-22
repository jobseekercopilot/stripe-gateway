package com.jobseekercopilot.stripegateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.stripegateway.client.PaymentServiceClient;
import com.jobseekercopilot.stripegateway.client.StripeApiClient;
import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.CreateOwnedCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateOwnedCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.ExpireOwnedCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.dto.ProviderPaymentEventRequest;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class StripeGatewayServiceTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000);

    @Test
    void checkoutSessionRequestCreatesStripeSession() {
        StripeApiClient stripeApiClient = mock(StripeApiClient.class);
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(stripeApiClient, paymentServiceClient);
        CreateCheckoutSessionRequest request = checkoutRequest();
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId("cs_test_123");
        session.setUrl("https://checkout.stripe.com/c/pay/cs_test_123");
        when(stripeApiClient.createCheckoutSession(eq(request), eq("Job Seeker Copilot AI Tokens - Starter")))
                .thenReturn(session);

        CreateCheckoutSessionResponse response = service.createCheckoutSession("user-123", request);

        assertThat(response.getSessionId()).isEqualTo("cs_test_123");
        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_123");
    }

    @Test
    void legacyCallerPricedCheckoutIsUnreachableWhenItsReleaseFlagIsDisabled() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeProperties properties = new StripeProperties();
        StripeGatewayService service = new StripeGatewayService(
                provider, verifier(), payments, properties);
        CreateCheckoutSessionRequest request = checkoutRequest();

        assertThatThrownBy(() -> service.createCheckoutSession("user-123", request))
                .isInstanceOf(
                        com.jobseekercopilot.stripegateway.exception.StripeConfigurationException.class)
                .hasMessage("Legacy caller-priced Stripe Checkout is disabled");

        verify(provider, never()).createCheckoutSession(eq(request), anyString());
    }

    @Test
    void checkoutRejectsConflictingCallerSelectedOwner() {
        StripeApiClient stripeApiClient = mock(StripeApiClient.class);
        StripeGatewayService service = service(
                stripeApiClient, mock(PaymentServiceClient.class));

        assertThatThrownBy(() -> service.createCheckoutSession("authenticated-owner", checkoutRequest()))
                .isInstanceOf(com.jobseekercopilot.stripegateway.exception.BadRequestException.class)
                .hasMessage("Payment owner does not match authenticated context");
    }

    @Test
    void checkoutSessionCompletedWebhookCallsPaymentService() throws Exception {
        StripeApiClient stripeApiClient = mock(StripeApiClient.class);
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(stripeApiClient, paymentServiceClient);
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

    @Test
    void webhookVerifierAcceptsAnyV1SignatureDuringSecretRotation() {
        StripeWebhookVerifier verifier = verifier();
        String payload = "{\"type\":\"checkout.session.completed\"}";
        long timestamp = NOW.getEpochSecond();
        String valid = hmac(timestamp + "." + payload);
        String invalid = "0".repeat(valid.length());

        assertThat(verifier.verifyAndParse(
                        payload,
                        "t=" + timestamp + ",v1=" + invalid + ",v1=" + valid)
                .path("type").asText())
                .isEqualTo("checkout.session.completed");
        assertThat(verifier.verifyAndParse(
                        payload,
                        "t=" + timestamp + ",v1=" + valid + ",v1=" + invalid)
                .path("type").asText())
                .isEqualTo("checkout.session.completed");
    }

    @Test
    void ownedCheckoutUsesOnlyAuthoritativePaymentOrderAndBindsSession() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot order = order("PENDING_CHECKOUT", null);
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId("cs_test_owned");
        session.setUrl("https://checkout.stripe.test/cs_test_owned");
        PaymentOrderSnapshot bound = order("CHECKOUT_OPEN", "cs_test_owned");
        when(payments.order("owner-123", order.getOrderId())).thenReturn(order);
        when(provider.createOwnedCheckoutSession(order)).thenReturn(session);
        when(payments.bindCheckoutSession("owner-123", order.getOrderId(), "cs_test_owned"))
                .thenReturn(bound);

        CreateOwnedCheckoutSessionResponse response = service.createOwnedCheckoutSession(
                "owner-123", "click-123", ownedRequest(order.getOrderId()));

        assertThat(response.getUrl()).isEqualTo("https://checkout.stripe.test/cs_test_owned");
        assertThat(response.getPromotionBonusDocumentCredits()).isEqualTo(13);
        verify(provider).createOwnedCheckoutSession(order);
        verify(payments).bindCheckoutSession("owner-123", order.getOrderId(), "cs_test_owned");
    }

    @Test
    void ambiguousRetryRetrievesAlreadyBoundSessionWithoutCreatingAnother() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot order = order("CHECKOUT_OPEN", "cs_test_owned");
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId("cs_test_owned");
        session.setUrl("https://checkout.stripe.test/cs_test_owned");
        when(payments.order("owner-123", order.getOrderId())).thenReturn(order);
        when(provider.retrieveOwnedCheckoutSession("cs_test_owned")).thenReturn(session);

        CreateOwnedCheckoutSessionResponse response = service.createOwnedCheckoutSession(
                "owner-123", "click-123", ownedRequest(order.getOrderId()));

        assertThat(response.getSessionId()).isEqualTo("cs_test_owned");
        verify(provider, never()).createOwnedCheckoutSession(order);
        verify(payments, never()).bindCheckoutSession(eq("owner-123"), eq(order.getOrderId()), anyString());
    }

    @Test
    void ownedCheckoutRejectsUnconfiguredTaxSnapshotBeforeCallingStripe() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot order = order("PENDING_CHECKOUT", null);
        order.setTaxStatus("NOT_CONFIGURED");
        when(payments.order("owner-123", order.getOrderId())).thenReturn(order);

        assertThatThrownBy(() -> service.createOwnedCheckoutSession(
                        "owner-123", "click-123", ownedRequest(order.getOrderId())))
                .isInstanceOf(com.jobseekercopilot.stripegateway.exception.BadRequestException.class)
                .hasMessage("Owned payment order is not valid for Checkout");

        verify(provider, never()).createOwnedCheckoutSession(order);
    }

    @Test
    void ownedCheckoutRejectsAnUnsafeRemainingProviderWindowBeforeStripeCreate() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot order = order("PENDING_CHECKOUT", null);
        order.setExpiresAt(Instant.now().plusSeconds(30 * 60));
        when(payments.order("owner-123", order.getOrderId())).thenReturn(order);

        assertThatThrownBy(() -> service.createOwnedCheckoutSession(
                        "owner-123", "click-unsafe-window", ownedRequest(order.getOrderId())))
                .isInstanceOf(com.jobseekercopilot.stripegateway.exception.BadRequestException.class)
                .hasMessageContaining("insufficient time remaining");

        verify(provider, never()).createOwnedCheckoutSession(order);
    }

    @Test
    void bindCommittedResponseLostIsReReadAndReturnsTheSameBoundSession() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot pending = order("PENDING_CHECKOUT", null);
        PaymentOrderSnapshot bound = order("CHECKOUT_OPEN", "cs_test_bound_lost");
        StripeCheckoutSession session = session("cs_test_bound_lost", "open");
        when(payments.order("owner-123", pending.getOrderId()))
                .thenReturn(pending, bound);
        when(provider.createOwnedCheckoutSession(pending)).thenReturn(session);
        when(payments.bindCheckoutSession(
                "owner-123", pending.getOrderId(), "cs_test_bound_lost"))
                .thenThrow(new ResourceAccessException("bind response lost"));

        CreateOwnedCheckoutSessionResponse response = service.createOwnedCheckoutSession(
                "owner-123", "click-bind-lost", ownedRequest(pending.getOrderId()));

        assertThat(response.getSessionId()).isEqualTo("cs_test_bound_lost");
        verify(provider, never()).expireOwnedCheckoutSession("cs_test_bound_lost");
        verify(payments, never()).cancelOrder("owner-123", pending.getOrderId());
    }

    @Test
    void rejectedBindExpiresProviderBeforeCancellingTheLocalOrder() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot pending = order("PENDING_CHECKOUT", null);
        PaymentOrderSnapshot cancelled = order("CANCELLED", null);
        StripeCheckoutSession session = session("cs_test_unbound", "open");
        StripeCheckoutSession expired = session("cs_test_unbound", "expired");
        when(payments.order("owner-123", pending.getOrderId()))
                .thenReturn(pending, pending);
        when(provider.createOwnedCheckoutSession(pending)).thenReturn(session);
        when(payments.bindCheckoutSession(
                "owner-123", pending.getOrderId(), "cs_test_unbound"))
                .thenThrow(new ResourceAccessException("bind rejected"));
        when(provider.expireOwnedCheckoutSession("cs_test_unbound")).thenReturn(expired);
        when(payments.cancelOrder("owner-123", pending.getOrderId())).thenReturn(cancelled);

        assertThatThrownBy(() -> service.createOwnedCheckoutSession(
                        "owner-123", "click-bind-rejected", ownedRequest(pending.getOrderId())))
                .isInstanceOf(
                        com.jobseekercopilot.stripegateway.exception.StripeCheckoutSagaException.class)
                .hasMessageContaining("safely expired");

        InOrder saga = inOrder(provider, payments);
        saga.verify(provider).expireOwnedCheckoutSession("cs_test_unbound");
        saga.verify(payments).cancelOrder("owner-123", pending.getOrderId());
    }

    @Test
    void expiryCommittedResponseLostIsReReadBeforeLocalCancellation() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot pending = order("PENDING_CHECKOUT", null);
        PaymentOrderSnapshot cancelled = order("CANCELLED", null);
        StripeCheckoutSession session = session("cs_test_expire_lost", "open");
        StripeCheckoutSession expired = session("cs_test_expire_lost", "expired");
        when(payments.order("owner-123", pending.getOrderId()))
                .thenReturn(pending, pending);
        when(provider.createOwnedCheckoutSession(pending)).thenReturn(session);
        when(payments.bindCheckoutSession(
                "owner-123", pending.getOrderId(), "cs_test_expire_lost"))
                .thenThrow(new ResourceAccessException("bind rejected"));
        when(provider.expireOwnedCheckoutSession("cs_test_expire_lost"))
                .thenThrow(new ResourceAccessException("expire response lost"));
        when(provider.retrieveOwnedCheckoutSession("cs_test_expire_lost"))
                .thenReturn(expired);
        when(payments.cancelOrder("owner-123", pending.getOrderId())).thenReturn(cancelled);

        assertThatThrownBy(() -> service.createOwnedCheckoutSession(
                        "owner-123", "click-expire-lost", ownedRequest(pending.getOrderId())))
                .isInstanceOf(
                        com.jobseekercopilot.stripegateway.exception.StripeCheckoutSagaException.class)
                .hasMessageContaining("safely expired");

        verify(provider).retrieveOwnedCheckoutSession("cs_test_expire_lost");
        verify(payments).cancelOrder("owner-123", pending.getOrderId());
    }

    @Test
    void localCancelCommittedResponseLostIsReReadAfterProviderExpiry() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot pending = order("PENDING_CHECKOUT", null);
        PaymentOrderSnapshot cancelled = order("CANCELLED", null);
        StripeCheckoutSession session = session("cs_test_cancel_lost", "open");
        StripeCheckoutSession expired = session("cs_test_cancel_lost", "expired");
        when(payments.order("owner-123", pending.getOrderId()))
                .thenReturn(pending, pending, cancelled);
        when(provider.createOwnedCheckoutSession(pending)).thenReturn(session);
        when(payments.bindCheckoutSession(
                "owner-123", pending.getOrderId(), "cs_test_cancel_lost"))
                .thenThrow(new ResourceAccessException("bind rejected"));
        when(provider.expireOwnedCheckoutSession("cs_test_cancel_lost")).thenReturn(expired);
        when(payments.cancelOrder("owner-123", pending.getOrderId()))
                .thenThrow(new ResourceAccessException("cancel response lost"));

        assertThatThrownBy(() -> service.createOwnedCheckoutSession(
                        "owner-123", "click-cancel-lost", ownedRequest(pending.getOrderId())))
                .isInstanceOf(
                        com.jobseekercopilot.stripegateway.exception.StripeCheckoutSagaException.class)
                .hasMessageContaining("safely expired");

        verify(payments).cancelOrder("owner-123", pending.getOrderId());
    }

    @Test
    void interruptedProviderCreateReplaysTheSameOrderBeforeReturningAnyUrl() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot pending = order("PENDING_CHECKOUT", null);
        PaymentOrderSnapshot bound = order("CHECKOUT_OPEN", "cs_test_replayed");
        StripeCheckoutSession sameSession = session("cs_test_replayed", "open");
        when(payments.order("owner-123", pending.getOrderId()))
                .thenReturn(pending, pending, pending);
        when(provider.createOwnedCheckoutSession(pending))
                .thenThrow(new ResourceAccessException("create response lost"))
                .thenThrow(new ResourceAccessException("provider still unavailable"))
                .thenReturn(sameSession);

        assertThatThrownBy(() -> service.createOwnedCheckoutSession(
                        "owner-123", "click-create-lost", ownedRequest(pending.getOrderId())))
                .isInstanceOf(
                        com.jobseekercopilot.stripegateway.exception.StripeCheckoutSagaException.class);
        verify(payments, never()).cancelOrder("owner-123", pending.getOrderId());

        when(payments.bindCheckoutSession(
                "owner-123", pending.getOrderId(), "cs_test_replayed"))
                .thenReturn(bound);
        CreateOwnedCheckoutSessionResponse recovered = service.createOwnedCheckoutSession(
                "owner-123", "click-create-lost", ownedRequest(pending.getOrderId()));

        assertThat(recovered.getSessionId()).isEqualTo("cs_test_replayed");
        verify(provider, times(3)).createOwnedCheckoutSession(pending);
    }

    @Test
    void ownedWebhookForwardsVerifiedCountryAmountModeAndOrderEvidence() throws Exception {
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(mock(StripeApiClient.class), payments);
        UUID orderId = UUID.fromString("1c05d1ab-e57b-4904-b627-e55a7132207c");
        String payload = """
                {"id":"evt_owned","type":"checkout.session.completed","livemode":false,
                 "created":1700000000,"data":{"object":{"id":"cs_test_owned",
                 "payment_intent":"pi_test_owned","payment_status":"paid","status":"complete",
                 "currency":"gbp","amount_total":1199,"client_reference_id":"%s",
                 "metadata":{"orderId":"%s"},
                 "customer_details":{"address":{"country":"GB"}}}}}
                """.formatted(orderId, orderId);

        service.handleWebhook(payload, signedHeader(payload));

        ArgumentCaptor<ProviderPaymentEventRequest> captor =
                ArgumentCaptor.forClass(ProviderPaymentEventRequest.class);
        verify(payments).providerEvent(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getBillingCountry()).isEqualTo("GB");
        assertThat(captor.getValue().getAmountTotalMinor()).isEqualTo(1199);
        assertThat(captor.getValue().getLiveMode()).isFalse();
        assertThat(captor.getValue().getPayloadSha256()).hasSize(64);
    }

    @Test
    void accountLifecycleExpiresOnlyMatchingCancelledOwnedSession() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot order = order("CANCELLED", "cs_test_owned");
        StripeCheckoutSession expired = new StripeCheckoutSession();
        expired.setId("cs_test_owned");
        expired.setStatus("expired");
        when(payments.order("owner-123", order.getOrderId())).thenReturn(order);
        when(provider.expireOwnedCheckoutSession("cs_test_owned")).thenReturn(expired);

        assertThat(service.expireOwnedCheckoutSession("owner-123",
                new ExpireOwnedCheckoutSessionRequest(order.getOrderId(), "cs_test_owned"))
                .status()).isEqualTo("EXPIRED");
    }

    @Test
    void accountLifecycleReportsCompleteWithoutTreatingItAsExpired() {
        StripeApiClient provider = mock(StripeApiClient.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        StripeGatewayService service = service(provider, payments);
        PaymentOrderSnapshot order = order("CHECKOUT_OPEN", "cs_test_paid");
        StripeCheckoutSession complete = session("cs_test_paid", "complete");
        complete.setPaymentStatus("paid");
        when(payments.order("owner-123", order.getOrderId())).thenReturn(order);
        when(provider.expireOwnedCheckoutSession("cs_test_paid")).thenReturn(complete);

        var response = service.expireOwnedCheckoutSession(
                "owner-123",
                new ExpireOwnedCheckoutSessionRequest(order.getOrderId(), "cs_test_paid"));

        assertThat(response.status()).isEqualTo("COMPLETE");
        assertThat(response.paymentStatus()).isEqualTo("PAID");
    }

    private PaymentOrderSnapshot order(String status, String sessionId) {
        PaymentOrderSnapshot order = new PaymentOrderSnapshot();
        order.setOrderId(UUID.fromString("1c05d1ab-e57b-4904-b627-e55a7132207c"));
        order.setOwnerId("owner-123");
        order.setStatus(status);
        order.setCatalogVersion("public-beta-2026-08-22");
        order.setPricingPlanId("active");
        order.setPricingPlanName("Active");
        order.setDocumentCredits(25);
        order.setPromotionBonusDocumentCredits(13);
        order.setPromotionGuaranteed(true);
        order.setPriceMinor(1199);
        order.setCurrency("GBP");
        order.setBillingCountry("GB");
        order.setTaxTreatment("VAT_NOT_CHARGED");
        order.setTaxStatus("NOT_VAT_REGISTERED");
        order.setLegalEntityType("SOLE_TRADER");
        order.setLegalEntityConfigurationVersion("seller-terms-v1");
        order.setDisplayedPriceIsCheckoutTotal(true);
        order.setExpiresAt(Instant.now().plusSeconds(60 * 60));
        order.setStripeSessionId(sessionId);
        return order;
    }

    private StripeCheckoutSession session(String id, String status) {
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId(id);
        session.setUrl("https://checkout.stripe.test/" + id);
        session.setStatus(status);
        return session;
    }

    private CreateOwnedCheckoutSessionRequest ownedRequest(UUID orderId) {
        CreateOwnedCheckoutSessionRequest request = new CreateOwnedCheckoutSessionRequest();
        request.setOrderId(orderId);
        return request;
    }

    private CreateCheckoutSessionRequest checkoutRequest() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setUserId("user-123");
        request.setPricingPlanId("starter");
        request.setTokenAmount(100000);
        request.setPriceGbpPence(499);
        return request;
    }

    private StripeGatewayService service(
            StripeApiClient provider, PaymentServiceClient payments) {
        StripeProperties properties = new StripeProperties();
        properties.setLegacyCheckoutEnabled(true);
        return new StripeGatewayService(provider, verifier(), payments, properties);
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

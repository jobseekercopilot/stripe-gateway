package com.jobseekercopilot.stripegateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore.FixtureTerminalEvent;
import com.jobseekercopilot.stripegateway.client.PaymentServiceClient;
import com.jobseekercopilot.stripegateway.client.StripeProviderClient;
import com.jobseekercopilot.stripegateway.config.ExternalProviderMode;
import com.jobseekercopilot.stripegateway.config.ExternalProviderProperties;
import com.jobseekercopilot.stripegateway.config.FixtureProperties;
import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.dto.ProviderPaymentEventRequest;
import com.jobseekercopilot.stripegateway.exception.BadRequestException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FixturePaymentControlServiceTest {
    private static final String SECRET =
            "fixture-webhook-signing-secret-000000000001";

    @Test
    void signedCompletedReplayUsesNormalWebhookBoundaryAndStableProviderEvent() {
        FixtureStripeSessionStore store = new FixtureStripeSessionStore();
        PaymentOrderSnapshot order = order();
        String sessionId = store.create(order).getId();
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FixtureProperties fixture = fixture();
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.FIXTURE);
        StripeWebhookVerifier verifier = new StripeWebhookVerifier(
                new StripeProperties(), provider, fixture, objectMapper);
        StripeGatewayService gateway = new StripeGatewayService(
                mock(StripeProviderClient.class), verifier, payments,
                new StripeProperties());
        FixturePaymentControlService service = new FixturePaymentControlService(
                store, fixture, gateway, objectMapper);

        var first = service.emit(sessionId, FixtureTerminalEvent.COMPLETED);
        var replay = service.emit(sessionId, FixtureTerminalEvent.COMPLETED);

        assertThat(replay.providerEventId()).isEqualTo(first.providerEventId());
        ArgumentCaptor<ProviderPaymentEventRequest> events =
                ArgumentCaptor.forClass(ProviderPaymentEventRequest.class);
        verify(payments, times(2)).providerEvent(events.capture());
        assertThat(events.getAllValues())
                .extracting(ProviderPaymentEventRequest::getProviderEventId)
                .containsOnly(first.providerEventId());
        assertThat(events.getAllValues().get(0).getPayloadSha256())
                .isEqualTo(events.getAllValues().get(1).getPayloadSha256());
        assertThat(events.getValue()).satisfies(event -> {
            assertThat(event.getOrderId()).isEqualTo(order.getOrderId());
            assertThat(event.getStripeSessionId()).isEqualTo(sessionId);
            assertThat(event.getPaymentStatus()).isEqualTo("paid");
            assertThat(event.getCheckoutStatus()).isEqualTo("complete");
            assertThat(event.getAmountTotalMinor()).isEqualTo(499);
            assertThat(event.getCurrency()).isEqualTo("gbp");
            assertThat(event.getBillingCountry()).isEqualTo("GB");
            assertThat(event.getLiveMode()).isFalse();
        });
    }

    @Test
    void fixtureVerifierRejectsUnsignedPayload() {
        ObjectMapper objectMapper = new ObjectMapper();
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.FIXTURE);
        StripeWebhookVerifier verifier = new StripeWebhookVerifier(
                new StripeProperties(), provider, fixture(), objectMapper);

        assertThatThrownBy(() -> verifier.verifyAndParse("{}", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing Stripe signature");
    }

    @Test
    void signedExpiredReplayUsesTheSameProviderEventAndNeverLooksPaid() {
        FixtureStripeSessionStore store = new FixtureStripeSessionStore();
        PaymentOrderSnapshot order = order();
        String sessionId = store.create(order).getId();
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FixtureProperties fixture = fixture();
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.FIXTURE);
        StripeGatewayService gateway = new StripeGatewayService(
                mock(StripeProviderClient.class),
                new StripeWebhookVerifier(
                        new StripeProperties(), provider, fixture, objectMapper),
                payments,
                new StripeProperties());
        FixturePaymentControlService service = new FixturePaymentControlService(
                store, fixture, gateway, objectMapper);

        var first = service.emit(sessionId, FixtureTerminalEvent.EXPIRED);
        var replay = service.emit(sessionId, FixtureTerminalEvent.EXPIRED);

        assertThat(replay.providerEventId()).isEqualTo(first.providerEventId());
        assertThat(replay.checkoutStatus()).isEqualTo("EXPIRED");
        assertThat(replay.paymentStatus()).isEqualTo("UNPAID");
        ArgumentCaptor<ProviderPaymentEventRequest> events =
                ArgumentCaptor.forClass(ProviderPaymentEventRequest.class);
        verify(payments, times(2)).providerEvent(events.capture());
        assertThat(events.getAllValues())
                .allSatisfy(event -> {
                    assertThat(event.getProviderEventId()).isEqualTo(first.providerEventId());
                    assertThat(event.getOrderId()).isEqualTo(order.getOrderId());
                    assertThat(event.getStripeSessionId()).isEqualTo(sessionId);
                    assertThat(event.getPaymentStatus()).isEqualTo("unpaid");
                    assertThat(event.getCheckoutStatus()).isEqualTo("expired");
                    assertThat(event.getLiveMode()).isFalse();
                });
        assertThat(events.getAllValues().get(0).getPayloadSha256())
                .isEqualTo(events.getAllValues().get(1).getPayloadSha256());
    }

    private FixtureProperties fixture() {
        FixtureProperties fixture = new FixtureProperties();
        fixture.setWebhookSecret(SECRET);
        return fixture;
    }

    private PaymentOrderSnapshot order() {
        PaymentOrderSnapshot order = new PaymentOrderSnapshot();
        order.setOrderId(UUID.randomUUID());
        order.setOwnerId("owner-123");
        order.setPricingPlanId("starter");
        order.setDocumentCredits(10);
        order.setPromotionBonusDocumentCredits(5);
        order.setPriceMinor(499);
        order.setCurrency("GBP");
        order.setBillingCountry("GB");
        order.setExpiresAt(Instant.now().plusSeconds(3600));
        return order;
    }
}

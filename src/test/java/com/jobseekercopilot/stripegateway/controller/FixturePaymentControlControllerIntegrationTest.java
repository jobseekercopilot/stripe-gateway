package com.jobseekercopilot.stripegateway.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore;
import com.jobseekercopilot.stripegateway.client.PaymentServiceClient;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.dto.ProviderPaymentEventRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "external-provider.mode=FIXTURE",
        "fixture.payment-control-enabled=true",
        "fixture.payment-control-token=fixture-payment-control-token-000000000001",
        "fixture.webhook-secret=fixture-webhook-signing-secret-000000000001"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FixturePaymentControlControllerIntegrationTest {
    private static final String TOKEN =
            "fixture-payment-control-token-000000000001";
    private static final String HEADER = "X-Environment-Data-Token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired FixtureStripeSessionStore store;
    @MockBean PaymentServiceClient paymentServiceClient;
    private String sessionId;
    private String owner;

    @BeforeEach
    void setUp() {
        owner = "owner-" + UUID.randomUUID();
        PaymentOrderSnapshot order = new PaymentOrderSnapshot();
        order.setOrderId(UUID.randomUUID());
        order.setOwnerId(owner);
        order.setPricingPlanId("starter");
        order.setDocumentCredits(10);
        order.setPromotionBonusDocumentCredits(5);
        order.setPriceMinor(799);
        order.setCurrency("GBP");
        order.setBillingCountry("GB");
        order.setExpiresAt(Instant.now().plusSeconds(3600));
        sessionId = store.create(order).getId();
    }

    @Test
    void invalidMissingAndDuplicateTokensCannotInspectMutateOrEmit() throws Exception {
        mockMvc.perform(get("/internal/fixtures/v2/stripe/owners/{owner}", owner))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(delete("/internal/fixtures/v2/stripe/owners/{owner}", owner)
                        .header(HEADER, "wrong"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/internal/fixtures/v2/stripe/checkout-sessions/{id}/events", sessionId)
                        .header(HEADER, TOKEN, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"COMPLETED\"}"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(paymentServiceClient);
        mockMvc.perform(get("/internal/fixtures/v2/stripe/owners/{owner}", owner)
                        .header(HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.sessions", hasSize(1)))
                .andExpect(jsonPath("$.details.sessions[0].status").value("open"));
    }

    @Test
    void validCompletedReplayTraversesSignedWebhookBoundaryWithStableEvidence()
            throws Exception {
        String first = emitCompleted();
        String replay = emitCompleted();
        String eventId = objectMapper.readTree(first).path("providerEventId").asText();

        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(replay).path("providerEventId").asText())
                .isEqualTo(eventId);
        verify(paymentServiceClient, times(2)).providerEvent(
                any(ProviderPaymentEventRequest.class));
        mockMvc.perform(get("/internal/fixtures/v2/stripe/owners/{owner}", owner)
                        .header(HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.sessions[0].status").value("complete"))
                .andExpect(jsonPath("$.details.sessions[0].payment_status").value("paid"));
    }

    private String emitCompleted() throws Exception {
        return mockMvc.perform(post(
                                "/internal/fixtures/v2/stripe/checkout-sessions/{id}/events",
                                sessionId)
                        .header(HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerSessionId").value(sessionId))
                .andExpect(jsonPath("$.checkoutStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andReturn().getResponse().getContentAsString();
    }
}

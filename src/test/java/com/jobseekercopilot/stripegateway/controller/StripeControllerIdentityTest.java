package com.jobseekercopilot.stripegateway.controller;

import com.jobseekercopilot.stripegateway.service.StripeGatewayService;
import com.jobseekercopilot.stripegateway.dto.ExpireOwnedCheckoutSessionResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StripeControllerIdentityTest {
    private static final String TOKEN =
            "payment-gateway-stripe-test-token-000000001";

    @Autowired private MockMvc mockMvc;
    @MockBean private StripeGatewayService stripeGatewayService;

    @Test
    void directOrForgedCheckoutCallsFailClosed() throws Exception {
        String request = """
                {"userId":"owner-123","pricingPlanId":"starter","tokenAmount":100000,"priceGbpPence":799}
                """;

        mockMvc.perform(post("/api/v1/stripe/checkout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/stripe/checkout-sessions")
                        .header("X-Service-Token", "forged")
                        .header("X-Payment-Owner", "owner-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_AUTHENTICATION_REQUIRED"));

        verify(stripeGatewayService, never()).createCheckoutSession(anyString(), any());
    }

    @Test
    void legacyOrAmbiguousOwnerContextIsRejected() throws Exception {
        String request = """
                {"userId":"owner-123","pricingPlanId":"starter","tokenAmount":100000,"priceGbpPence":799}
                """;

        mockMvc.perform(post("/api/v1/stripe/checkout-sessions")
                        .header("X-Service-Token", TOKEN)
                        .header("X-Payment-Owner", "owner-123")
                        .header("X-User-Id", "victim-456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALLER_IDENTITY_REJECTED"));

        mockMvc.perform(post("/api/v1/stripe/checkout-sessions")
                        .header("X-Service-Token", TOKEN)
                        .header("X-Payment-Owner", "owner-123", "victim-456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_OWNER_REQUIRED"));

        verify(stripeGatewayService, never()).createCheckoutSession(anyString(), any());
    }

    @Test
    void lifecycleSessionExpiryRequiresDistinctTrustedIdentityAndOwner() throws Exception {
        String lifecycleToken = "payment-to-stripe-lifecycle-test-token-000001";
        UUID orderId = UUID.fromString("1c05d1ab-e57b-4904-b627-e55a7132207c");
        when(stripeGatewayService.expireOwnedCheckoutSession(anyString(), any()))
                .thenReturn(new ExpireOwnedCheckoutSessionResponse(
                        orderId, "cs_test_owned", "EXPIRED"));
        String body = "{\"orderId\":\"" + orderId
                + "\",\"providerSessionId\":\"cs_test_owned\"}";

        mockMvc.perform(post("/internal/v2/stripe/checkout-sessions/expire")
                        .header("X-Service-Token", TOKEN)
                        .header("X-Payment-Owner", "owner-123")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v2/stripe/checkout-sessions/expire")
                        .header("X-Service-Token", lifecycleToken)
                        .header("X-Payment-Owner", "owner-123")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }
}

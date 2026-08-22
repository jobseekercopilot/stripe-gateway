package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.stripegateway.security.StripeGatewayCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentServiceClientTest {
    @Test
    void bindsStripeServiceIdentityAndSignedMetadataOwner() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://payment.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String paymentServiceToken = "stripe-payment-service-token-00000000000001";
        PaymentServiceClient client = new PaymentServiceClient(
                builder.build(),
                new StripeGatewayCredentials(
                        "payment-gateway-stripe-token-00000000000001",
                        paymentServiceToken));
        ConfirmStripePurchaseRequest request = ConfirmStripePurchaseRequest.builder()
                .userId("owner-123")
                .pricingPlanId("starter")
                .tokenAmount(100000)
                .stripeSessionId("cs_test_123")
                .build();

        server.expect(requestTo("https://payment.example.test/api/v1/payments/confirm-stripe-purchase"))
                .andExpect(header("X-Service-Token", paymentServiceToken))
                .andExpect(header("X-Payment-Owner", "owner-123"))
                .andRespond(withSuccess());

        client.confirmStripePurchase(request);

        server.verify();
    }
}

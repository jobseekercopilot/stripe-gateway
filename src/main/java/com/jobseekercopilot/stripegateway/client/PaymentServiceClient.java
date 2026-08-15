package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.stripegateway.dto.BindCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.dto.ProviderPaymentEventRequest;
import java.util.UUID;
import com.jobseekercopilot.stripegateway.security.StripeGatewayCredentials;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentServiceClient {
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String OWNER_HEADER = "X-Payment-Owner";

    private final RestClient paymentServiceRestClient;
    private final StripeGatewayCredentials credentials;

    public PaymentServiceClient(
            @Qualifier("paymentServiceRestClient") RestClient paymentServiceRestClient,
            StripeGatewayCredentials credentials) {
        this.paymentServiceRestClient = paymentServiceRestClient;
        this.credentials = credentials;
    }

    public void confirmStripePurchase(ConfirmStripePurchaseRequest request) {
        paymentServiceRestClient.post()
                .uri("/api/v1/payments/confirm-stripe-purchase")
                .header(SERVICE_TOKEN_HEADER, credentials.paymentServiceToken())
                .header(OWNER_HEADER, request.getUserId())
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public PaymentOrderSnapshot order(String owner, UUID orderId) {
        return paymentServiceRestClient.get()
                .uri("/api/v2/payments/orders/{orderId}", orderId)
                .header(SERVICE_TOKEN_HEADER, credentials.paymentServiceToken())
                .header(OWNER_HEADER, owner)
                .retrieve()
                .body(PaymentOrderSnapshot.class);
    }

    public PaymentOrderSnapshot bindCheckoutSession(
            String owner, UUID orderId, String stripeSessionId) {
        return paymentServiceRestClient.post()
                .uri("/api/v2/payments/orders/{orderId}/bind-stripe-session", orderId)
                .header(SERVICE_TOKEN_HEADER, credentials.paymentServiceToken())
                .header(OWNER_HEADER, owner)
                .body(new BindCheckoutSessionRequest(stripeSessionId))
                .retrieve()
                .body(PaymentOrderSnapshot.class);
    }

    public void providerEvent(ProviderPaymentEventRequest request) {
        paymentServiceRestClient.post()
                .uri("/api/v2/payments/provider-events/stripe")
                .header(SERVICE_TOKEN_HEADER, credentials.paymentServiceToken())
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
